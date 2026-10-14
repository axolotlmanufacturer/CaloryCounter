"""Reads the BLS 4.0 data table (Max Rubner-Institut, CC BY 4.0) into food rows.

Column mapping (nutrient code in the header -> database column), all per 100 g:

    ENERCC   Energie (kcal)                 -> kcal
    PROT625  Protein (N x 6,25)             -> protein
    CHO      Kohlenhydrate, verfügbar       -> carbs
    SUGAR    Zucker, gesamt                 -> sugar
    FAT      Fett                           -> fat
    FIBT     Ballaststoffe, gesamt          -> fiber
    NACL     Salz (Natriumchlorid)          -> salt   (else NA, Natrium in mg: salt = sodium x 2.5)

Cell values: a number is taken as is; ``-`` means "not determined" and becomes NULL; the markers ``<LOD``,
``<LOQ`` (below the limit of detection / quantification) and ``TR`` (trace) become 0.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import openpyxl

from normalize import normalize

CODE_COLUMNS = {
    "ENERCC": "kcal",
    "PROT625": "protein",
    "CHO": "carbs",
    "SUGAR": "sugar",
    "FAT": "fat",
    "FIBT": "fiber",
    "NACL": "salt",
    "NA": "sodium_mg",
}

BELOW_DETECTION = {"<LOD", "<LOQ", "<LOD or <LOQ", "TR"}
SODIUM_TO_SALT = 2.5


@dataclass(frozen=True)
class BlsFood:
    code: str
    name: str
    search_text: str
    kcal: float
    protein: float | None
    carbs: float | None
    sugar: float | None
    fat: float | None
    fiber: float | None
    salt: float | None


def parse_cell(value: object) -> float | None:
    """A BLS cell as a number: NULL when not determined, 0 when below detection or only a trace."""
    if value is None:
        return None
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if text in BELOW_DETECTION:
        return 0.0
    if text in ("", "-"):
        return None
    try:
        return float(text.replace(",", "."))
    except ValueError:
        return None


def find_columns(header: tuple) -> dict[str, int]:
    """Index of the value column of every nutrient we need, found by the code at the start of the header."""
    found: dict[str, int] = {}
    for index, title in enumerate(header):
        if not isinstance(title, str) or "[" not in title:
            continue
        code = title.split(" ", 1)[0]
        if code in CODE_COLUMNS and code not in found:
            found[code] = index
    missing = set(CODE_COLUMNS) - set(found)
    if missing:
        raise ValueError(f"BLS table is missing nutrient columns: {sorted(missing)}")
    return found


def read_bls(path: Path) -> list[BlsFood]:
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        rows = workbook.worksheets[0].iter_rows(values_only=True)
        header = next(rows)
        columns = find_columns(header)
        foods: list[BlsFood] = []
        for row in rows:
            code, name = row[0], row[1]
            if not code or not name:
                continue
            values = {field: parse_cell(row[columns[code_]]) for code_, field in CODE_COLUMNS.items()}
            kcal = values["kcal"]
            if kcal is None:
                continue
            salt = values["salt"]
            if salt is None and values["sodium_mg"] is not None:
                salt = values["sodium_mg"] / 1000.0 * SODIUM_TO_SALT
            clean_name = " ".join(str(name).split())
            foods.append(
                BlsFood(
                    code=str(code).strip(),
                    name=clean_name,
                    search_text=normalize(clean_name),
                    kcal=kcal,
                    protein=values["protein"],
                    carbs=values["carbs"],
                    sugar=values["sugar"],
                    fat=values["fat"],
                    fiber=values["fiber"],
                    salt=salt,
                )
            )
        return foods
    finally:
        workbook.close()
