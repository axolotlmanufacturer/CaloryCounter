"""Open Food Facts (Germany) -> food rows, with every filter counted for the build report.

Sources (both from OFF's official S3 bucket, see README.md):

* ``en.openfoodfacts.org.products.csv.gz`` - tab separated, nutrients per 100 g/ml, countries, scans.
* ``openfoodfacts-mobile-dump-products.tsv.gz`` - per-language product names (the CSV has no
  ``product_name_de``), joined on ``code``.

Filters, in order (PROMPT.md section 5.1): valid barcode, a name, an energy value, sane ranges,
kcal and kJ agree, kcal agrees with the macros (only egregious mismatches), then one row per barcode.
"""

from __future__ import annotations

import re
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Iterator

import duckdb

from gtin import barcode_key, is_valid_gtin
from normalize import normalize

KJ_PER_KCAL = 4.184
MAX_NAME_LENGTH = 160
MAX_BRAND_LENGTH = 60
MAX_SERVING_LABEL_LENGTH = 40
MAX_SERVING_G = 5000.0

# {'lang': de, 'text': Past Vollmilch}, {'lang': fr, ...}: the text ends at "}" before the next entry or the end.
GERMAN_NAME_PATTERN = r"\{'lang': de, 'text': (.*?)\}(?:, \{'lang'|\]$)"

RAW_COLUMNS = (
    "code name_de name_csv brands quantity serving_size serving_quantity last_modified unique_scans "
    "completeness kcal kj fat carbs sugar fiber protein salt sodium alcohol"
).split()


@dataclass(frozen=True)
class RawOffRow:
    """One product as read from the files: everything still text (or None)."""

    code: str
    name_de: str | None = None
    name_csv: str | None = None
    brands: str | None = None
    quantity: str | None = None
    serving_size: str | None = None
    serving_quantity: str | None = None
    last_modified: str | None = None
    unique_scans: str | None = None
    completeness: str | None = None
    kcal: str | None = None
    kj: str | None = None
    fat: str | None = None
    carbs: str | None = None
    sugar: str | None = None
    fiber: str | None = None
    protein: str | None = None
    salt: str | None = None
    sodium: str | None = None
    alcohol: str | None = None


@dataclass(frozen=True)
class OffFood:
    barcode: str
    name: str
    brand: str | None
    search_text: str
    kcal: float
    protein: float | None
    carbs: float | None
    sugar: float | None
    fat: float | None
    fiber: float | None
    salt: float | None
    per_unit: str
    serving_g: float | None
    serving_label: str | None
    popularity: int
    last_modified: int
    completeness: float


# --- the filter steps -------------------------------------------------------------------------------

STEP_BARCODE = "valid barcode (8/12/13/14 digits, check digit)"
STEP_NAME = "has a name"
STEP_ENERGY = "has an energy value (kcal, else kJ / 4,184)"
STEP_SANITY = "sane values (kcal 0-900, each nutrient 0-100 g, protein + carbs + fat <= 105 g)"
STEP_KJ = "kcal and kJ do not contradict each other"
STEP_MACROS = "kcal fits protein/carbs/fat (only egregious mismatches dropped)"
STEP_DEDUPE = "one row per barcode (most recently modified wins)"
STEPS = (STEP_BARCODE, STEP_NAME, STEP_ENERGY, STEP_SANITY, STEP_KJ, STEP_MACROS, STEP_DEDUPE)


@dataclass
class FilterReport:
    """Rows in and out of every filter step."""

    start_label: str = ""
    start_rows: int = 0
    rejected: "OrderedDict[str, int]" = field(default_factory=lambda: OrderedDict((s, 0) for s in STEPS))

    def lines(self) -> list[str]:
        out = [f"{self.start_label}: {self.start_rows:,}"]
        remaining = self.start_rows
        for step, rejected in self.rejected.items():
            out.append(f"  {step}: {remaining:,} -> {remaining - rejected:,}  (-{rejected:,})")
            remaining -= rejected
        out.append(f"kept: {remaining:,}")
        return out

    @property
    def kept(self) -> int:
        return self.start_rows - sum(self.rejected.values())


def parse_number(text: str | None) -> float | None:
    if text is None:
        return None
    text = text.strip()
    if not text:
        return None
    try:
        value = float(text.replace(",", "."))
    except ValueError:
        return None
    return value if value == value and abs(value) != float("inf") else None


def clean_text(text: str | None, limit: int) -> str | None:
    if text is None:
        return None
    cleaned = " ".join(text.split())
    if not cleaned:
        return None
    if len(cleaned) > limit:
        cleaned = cleaned[: limit - 1].rstrip() + "…"
    return cleaned


def pick_name(row: RawOffRow) -> str | None:
    """The German name if the product has one, else the main-language name; needs at least one letter."""
    for candidate in (row.name_de, row.name_csv):
        name = clean_text(candidate, MAX_NAME_LENGTH)
        if name and re.search(r"[a-z]", normalize(name)):
            return name
    return None


def pick_energy(row: RawOffRow) -> tuple[float | None, float | None]:
    """(kcal, kJ) as stated; kcal falls back to kJ / 4,184."""
    kcal, kj = parse_number(row.kcal), parse_number(row.kj)
    if kcal is None and kj is not None:
        kcal = kj / KJ_PER_KCAL
    return kcal, kj


def nutrient(row: RawOffRow, attr: str) -> float | None:
    return parse_number(getattr(row, attr))


def salt_of(row: RawOffRow) -> float | None:
    salt = nutrient(row, "salt")
    if salt is None:
        sodium = nutrient(row, "sodium")
        salt = sodium * 2.5 if sodium is not None else None
    return salt


def is_sane(kcal: float, row: RawOffRow) -> bool:
    if not 0 <= kcal <= 900:
        return False
    grams = [nutrient(row, a) for a in ("fat", "carbs", "sugar", "fiber", "protein")] + [salt_of(row)]
    if any(g is not None and not 0 <= g <= 100 for g in grams):
        return False
    macros = [nutrient(row, a) for a in ("protein", "carbs", "fat")]
    return sum(m for m in macros if m is not None) <= 105


def energies_agree(kcal: float, kj: float | None) -> bool:
    """False when kcal and kJ were both given but are far apart (often kJ typed into the kcal field)."""
    if kj is None:
        return True
    from_kj = kj / KJ_PER_KCAL
    difference = abs(kcal - from_kj)
    return not (difference > 50 and difference > 0.3 * max(kcal, from_kj))


def fits_macros(kcal: float, row: RawOffRow) -> bool:
    """Compares with 4/4/9 kcal per g; alcohol is added at 7 kcal per g. Only egregious mismatches fail
    (more than 80 kcal and more than 50 % of the larger value), because fibre, polyols and rounding
    leave room."""
    protein, carbs, fat = (nutrient(row, a) for a in ("protein", "carbs", "fat"))
    if protein is None or carbs is None or fat is None:
        return True
    expected = 4 * protein + 4 * carbs + 9 * fat + 7 * (nutrient(row, "alcohol") or 0.0)
    difference = abs(kcal - expected)
    return not (difference > 80 and difference > 0.5 * max(kcal, expected))


_QUANTITY = re.compile(r"(\d+(?:[.,]\d+)?)\s*(ml|cl|dl|l|litre|liter|fl\.?\s*oz)\b", re.IGNORECASE)


def per_unit_of(quantity: str | None) -> str:
    """Drinks (quantity in ml, cl, dl or l) are per 100 ml, everything else per 100 g."""
    return "ml" if quantity and _QUANTITY.search(quantity) else "g"


def pick_brand(brands: str | None) -> tuple[str | None, str | None]:
    """(brand to show, all brands for the search text)."""
    if not brands:
        return None, None
    parts = [p.strip() for p in brands.split(",") if p.strip()]
    if not parts:
        return None, None
    return clean_text(parts[0], MAX_BRAND_LENGTH), clean_text(" ".join(parts), 200)


def pick_serving(row: RawOffRow) -> tuple[float | None, str | None]:
    grams = parse_number(row.serving_quantity)
    if grams is None or not 0 < grams <= MAX_SERVING_G:
        return None, None
    return grams, clean_text(row.serving_size, MAX_SERVING_LABEL_LENGTH)


def convert(row: RawOffRow, report: FilterReport) -> OffFood | None:
    """Applies the row filters in order; counts the first one that rejects the row."""

    def reject(step: str) -> None:
        report.rejected[step] += 1

    code = row.code.strip()
    if not is_valid_gtin(code):
        return reject(STEP_BARCODE)
    name = pick_name(row)
    if name is None:
        return reject(STEP_NAME)
    kcal, kj = pick_energy(row)
    if kcal is None:
        return reject(STEP_ENERGY)
    if not is_sane(kcal, row):
        return reject(STEP_SANITY)
    if not energies_agree(kcal, kj):
        return reject(STEP_KJ)
    if not fits_macros(kcal, row):
        return reject(STEP_MACROS)

    brand, brands_full = pick_brand(row.brands)
    serving_g, serving_label = pick_serving(row)
    return OffFood(
        barcode=code,
        name=name,
        brand=brand,
        search_text=normalize(f"{name} {brands_full or ''}"),
        kcal=kcal,
        protein=nutrient(row, "protein"),
        carbs=nutrient(row, "carbs"),
        sugar=nutrient(row, "sugar"),
        fat=nutrient(row, "fat"),
        fiber=nutrient(row, "fiber"),
        salt=salt_of(row),
        per_unit=per_unit_of(row.quantity),
        serving_g=serving_g,
        serving_label=serving_label,
        popularity=int(parse_number(row.unique_scans) or 0),
        last_modified=int(parse_number(row.last_modified) or 0),
        completeness=parse_number(row.completeness) or 0.0,
    )


def convert_all(rows: Iterable[RawOffRow], report: FilterReport) -> list[OffFood]:
    """Filters every row, then keeps one row per barcode: the most recently modified one."""
    best: dict[str, OffFood] = {}
    for row in rows:
        report.start_rows += 1
        food = convert(row, report)
        if food is None:
            continue
        key = barcode_key(food.barcode)
        current = best.get(key)
        if current is None:
            best[key] = food
        else:
            report.rejected[STEP_DEDUPE] += 1
            if _newer(food, current):
                best[key] = food
    return list(best.values())


def _newer(candidate: OffFood, current: OffFood) -> bool:
    return (candidate.last_modified, candidate.completeness, candidate.popularity) > (
        current.last_modified,
        current.completeness,
        current.popularity,
    )


def rank_by_popularity(foods: list[OffFood]) -> list[OffFood]:
    """Most scanned first, then shorter names, then barcode: the order the app wants results in."""
    return sorted(foods, key=lambda f: (-f.popularity, len(f.name), f.barcode))


# --- reading the files ------------------------------------------------------------------------------

_CSV_OPTIONS = "delim='\t', header=true, all_varchar=true, quote='', escape='', ignore_errors=true, strict_mode=false"


def read_off(csv_path: Path, mobile_path: Path | None, report: FilterReport) -> Iterator[RawOffRow]:
    """Germany's products from the CSV, German names from the mobile dump, in chunks."""
    con = duckdb.connect()
    csv_sql = f"read_csv('{csv_path.as_posix()}', {_CSV_OPTIONS})"
    total = con.execute(f"SELECT count(*) FROM {csv_sql}").fetchone()[0]
    report.start_label = f"OFF products in Germany (of {total:,} in the CSV)"

    if mobile_path is not None:
        names = (
            f"SELECT code, regexp_extract(product_name, ?, 1) AS name_de FROM "
            f"read_csv('{mobile_path.as_posix()}', {_CSV_OPTIONS})"
        )
        params: list = [GERMAN_NAME_PATTERN]
    else:
        names = "SELECT NULL::VARCHAR AS code, NULL::VARCHAR AS name_de WHERE false"
        params = []
    query = f"""
        SELECT c.code, nullif(m.name_de, '') AS name_de, c.product_name AS name_csv, c.brands, c.quantity,
               c.serving_size, c.serving_quantity, c.last_modified_t AS last_modified,
               c.unique_scans_n AS unique_scans, c.completeness,
               c."energy-kcal_100g" AS kcal, c."energy-kj_100g" AS kj, c.fat_100g AS fat,
               c.carbohydrates_100g AS carbs, c.sugars_100g AS sugar, c.fiber_100g AS fiber,
               c.proteins_100g AS protein, c.salt_100g AS salt, c.sodium_100g AS sodium, c.alcohol_100g AS alcohol
        FROM {csv_sql} c
        LEFT JOIN (SELECT code, any_value(name_de) AS name_de FROM ({names}) GROUP BY code) m ON m.code = c.code
        WHERE c.countries_tags LIKE '%en:germany%'
    """
    cursor = con.execute(query, params)
    while True:
        chunk = cursor.fetchmany(50_000)
        if not chunk:
            break
        for values in chunk:
            yield RawOffRow(**dict(zip(RAW_COLUMNS, values)))
