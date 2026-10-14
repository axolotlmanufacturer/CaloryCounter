#!/usr/bin/env python3
"""Builds ``foods.db``, the read-only food database of the app (PROMPT.md section 5.1).

Sources: BLS 4.0 (Max Rubner-Institut) and Open Food Facts, Germany. Usage::

    .venv/bin/python build_food_db.py --sample 3000     # small development database
    .venv/bin/python build_food_db.py                   # the full database (release)

The tables are created from the ``createSql`` of Room's exported schema, so the file passes Room's
``createFromAsset`` validation (see README.md). Ids: BLS foods first, then Open Food Facts foods by
falling popularity, which makes ``ORDER BY id`` the relevance order.
"""

from __future__ import annotations

import argparse
import csv
import json
import sqlite3
import sys
import time
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from bls_source import BlsFood, read_bls
from off_source import FilterReport, OffFood, convert_all, rank_by_popularity, read_off

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
SCHEMA_DIR = REPO / "app" / "schemas" / "de.privat.kalorienzaehler.data.fooddb.FoodDatabase"
BLS_XLSX = ROOT / "data/raw/bls/BLS_4_0_2025_DE/BLS_4_0_Daten_2025_DE.xlsx"
OFF_CSV = ROOT / "data/raw/off/en.openfoodfacts.org.products.csv.gz"
OFF_MOBILE = ROOT / "data/raw/off/openfoodfacts-mobile-dump-products.tsv.gz"
OFF_DATE_FILE = ROOT / "data/raw/off/export_date.txt"
PORTIONS_CSV = ROOT / "portions_de.csv"

BLS_VERSION = "BLS 4.0 (2025), Max Rubner-Institut, CC BY 4.0"
SIZE_BUDGET_MB = 120

FOOD_COLUMNS = (
    "id source source_id barcode name brand search_text kcal protein carbs sugar fat fiber salt "
    "per_unit serving_g serving_label popularity"
).split()


@dataclass(frozen=True)
class Portion:
    bls_code: str
    label: str
    grams: float


@dataclass(frozen=True)
class Schema:
    version: int
    statements: list[str]  # tables, indices and the room_master_table setup, in creation order


def load_schema(path: Path | None = None) -> Schema:
    """Room's exported schema of the food database (the highest version found, or ``path``)."""
    if path is None:
        files = sorted(SCHEMA_DIR.glob("*.json"), key=lambda p: int(p.stem))
        if not files:
            raise SystemExit(f"No exported Room schema in {SCHEMA_DIR}. Run ./gradlew :app:kspDebugKotlin first.")
        path = files[-1]
    database = json.loads(path.read_text(encoding="utf-8"))["database"]
    statements: list[str] = []
    for entity in database["entities"]:
        table = entity["tableName"]
        statements.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            statements.append(index["createSql"].replace("${TABLE_NAME}", table))
    statements.extend(database["setupQueries"])
    return Schema(version=int(database["version"]), statements=statements)


def read_portions(path: Path) -> list[Portion]:
    with path.open(encoding="utf-8", newline="") as handle:
        return [Portion(r["bls_code"].strip(), r["label"].strip(), float(r["grams"])) for r in csv.DictReader(handle)]


# --- choosing what goes in --------------------------------------------------------------------------


def select_sample(
    bls: list[BlsFood], off_ranked: list[OffFood], portions: list[Portion], total: int
) -> tuple[list[BlsFood], list[OffFood]]:
    """A small, deterministic database for development: a third BLS (every food with a portion, the rest
    evenly spread over the code list), the rest the most scanned Open Food Facts products."""
    wanted_bls = max(1, total // 3)
    with_portions = {p.bls_code for p in portions}
    chosen = {f.code for f in bls if f.code in with_portions}
    remaining = [f for f in bls if f.code not in chosen]
    missing = wanted_bls - len(chosen)
    if missing > 0 and remaining:
        step = max(1, len(remaining) // missing)
        chosen.update(f.code for f in remaining[::step][:missing])
    sample_bls = [f for f in bls if f.code in chosen]
    return sample_bls, off_ranked[: max(0, total - len(sample_bls))]


# --- writing the database ---------------------------------------------------------------------------


def create_database(path: Path, schema: Schema) -> sqlite3.Connection:
    if path.exists():
        path.unlink()
    path.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(path)
    for statement in schema.statements:
        con.execute(statement)
    return con


def write_foods(con: sqlite3.Connection, bls: list[BlsFood], off: list[OffFood]) -> dict[str, int]:
    """Inserts the foods and returns the BLS code -> id map."""
    placeholders = ",".join("?" * len(FOOD_COLUMNS))
    sql = f"INSERT INTO foods ({','.join(f'`{c}`' for c in FOOD_COLUMNS)}) VALUES ({placeholders})"
    rows = []
    ids: dict[str, int] = {}
    next_id = 1
    for f in bls:
        ids[f.code] = next_id
        rows.append(
            (next_id, "BLS", f.code, None, f.name, None, f.search_text, f.kcal, f.protein, f.carbs, f.sugar, f.fat,
             f.fiber, f.salt, "g", None, None, 0)
        )
        next_id += 1
    for f in off:
        rows.append(
            (next_id, "OFF", f.barcode, f.barcode, f.name, f.brand, f.search_text, f.kcal, f.protein, f.carbs,
             f.sugar, f.fat, f.fiber, f.salt, f.per_unit, f.serving_g, f.serving_label, f.popularity)
        )
        next_id += 1
    con.executemany(sql, rows)
    return ids


def write_portions(con: sqlite3.Connection, portions: list[Portion], ids: dict[str, int]) -> int:
    """Typical portions; every one is an estimate (``estimated = 1``) and listed in README.md for review."""
    rows = [(ids[p.bls_code], p.label, p.grams, 1) for p in portions if p.bls_code in ids]
    con.executemany("INSERT INTO food_portions (food_id, label, grams, estimated) VALUES (?,?,?,?)", rows)
    return len(rows)


def write_meta(con: sqlite3.Connection, meta: dict[str, str]) -> None:
    con.executemany("INSERT INTO food_db_meta (key, value) VALUES (?,?)", sorted(meta.items()))


def finish_database(con: sqlite3.Connection, schema: Schema) -> None:
    """Search index, Room's version marker, statistics and a compact file."""
    con.execute("INSERT INTO foods_fts(foods_fts) VALUES('rebuild')")
    con.execute(f"PRAGMA user_version = {schema.version}")
    con.commit()
    con.execute("ANALYZE")
    con.commit()
    con.execute("VACUUM")


def table_sizes(path: Path) -> list[tuple[str, int]]:
    """Bytes per table/index from the ``dbstat`` virtual table, when this SQLite has it."""
    con = sqlite3.connect(path)
    try:
        rows = con.execute("SELECT name, SUM(pgsize) FROM dbstat GROUP BY name ORDER BY 2 DESC").fetchall()
        return [(name, int(size)) for name, size in rows]
    except sqlite3.DatabaseError:
        return []
    finally:
        con.close()


# --- orchestration ----------------------------------------------------------------------------------


def build(args: argparse.Namespace) -> list[str]:
    started = time.time()
    schema = load_schema(args.schema)
    portions = read_portions(args.portions)
    report: list[str] = []

    bls = read_bls(args.bls)
    report.append(f"BLS foods read: {len(bls):,}")

    off_report = FilterReport()
    off = rank_by_popularity(convert_all(read_off(args.off_csv, args.off_mobile, off_report), off_report))
    report.extend(off_report.lines())

    if args.sample:
        bls, off = select_sample(bls, off, portions, args.sample)
        report.append(f"sample of {args.sample:,}: {len(bls):,} BLS + {len(off):,} OFF foods")

    out = args.out or ROOT / "out" / ("foods-sample.db" if args.sample else "foods-full.db")
    con = create_database(out, schema)
    ids = write_foods(con, bls, off)
    portion_count = write_portions(con, portions, ids)
    write_meta(
        con,
        {
            "build_date": args.build_date,
            "bls_version": BLS_VERSION,
            "off_export_date": args.off_date,
            "bls_count": str(len(bls)),
            "off_count": str(len(off)),
            "portion_count": str(portion_count),
            "schema_version": str(schema.version),
            "sample": "true" if args.sample else "false",
        },
    )
    finish_database(con, schema)
    con.close()

    size_mb = out.stat().st_size / 1_000_000
    report.append(f"final foods: {len(bls) + len(off):,} ({len(bls):,} BLS, {len(off):,} OFF), portions: {portion_count}")
    report.append(f"database: {out}  {size_mb:,.1f} MB")
    sizes = table_sizes(out)
    if sizes:
        report.append("size by table/index:")
        report.extend(f"  {name}: {size / 1_000_000:,.1f} MB" for name, size in sizes[:10])
    if size_mb > SIZE_BUDGET_MB:
        report.append(f"WARNING: larger than the {SIZE_BUDGET_MB} MB budget - propose tighter filters (PROMPT.md 5.1)")
    report.append(f"built in {time.time() - started:,.0f} s")
    return report


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sample", type=int, metavar="N", help="build a small development database with about N foods")
    parser.add_argument("--out", type=Path, help="output file (default: out/foods-sample.db or out/foods-full.db)")
    parser.add_argument("--report", type=Path, help="write the build report to this file (default: next to the database)")
    parser.add_argument("--schema", type=Path, help="Room schema JSON (default: the newest in app/schemas)")
    parser.add_argument("--bls", type=Path, default=BLS_XLSX)
    parser.add_argument("--off-csv", type=Path, default=OFF_CSV)
    parser.add_argument("--off-mobile", type=Path, default=OFF_MOBILE)
    parser.add_argument("--portions", type=Path, default=PORTIONS_CSV)
    parser.add_argument("--build-date", default=date.today().isoformat())
    default_off_date = OFF_DATE_FILE.read_text().strip() if OFF_DATE_FILE.exists() else "unbekannt"
    parser.add_argument("--off-date", default=default_off_date, help="date of the Open Food Facts export")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    report = build(args)
    text = "\n".join(report) + "\n"
    print(text)
    out = args.out or ROOT / "out" / ("foods-sample.db" if args.sample else "foods-full.db")
    (args.report or out.with_suffix(".report.txt")).write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
