"""The generated database must be what Room expects, and search must work on it."""

import json
import sqlite3
from pathlib import Path

import pytest

import build_food_db as builder
from bls_source import BlsFood
from normalize import normalize
from off_source import OffFood

SCHEMA_FILE = max(builder.SCHEMA_DIR.glob("*.json"), key=lambda p: int(p.stem))


def bls(code, name, kcal=100.0, **kw):
    return BlsFood(code, name, normalize(name), kcal, kw.get("protein"), kw.get("carbs"), kw.get("sugar"),
                   kw.get("fat"), kw.get("fiber"), kw.get("salt"))


def off(barcode, name, popularity, brand=None, kcal=200.0, per_unit="g", serving=None):
    return OffFood(barcode, name, brand, normalize(f"{name} {brand or ''}"), kcal, 5.0, 20.0, 2.0, 8.0, 1.0, 0.5,
                   per_unit, serving[0] if serving else None, serving[1] if serving else None, popularity, 0, 0.5)


@pytest.fixture
def database(tmp_path):
    schema = builder.load_schema()
    path = tmp_path / "foods.db"
    con = builder.create_database(path, schema)
    bls_foods = [bls("M402600", "Gouda 48 % Fett i. Tr.", 379, protein=25.0, fat=30.0), bls("F110100", "Apfel roh", 58)]
    off_foods = [
        off("4006381333931", "Käse Scheiben", 5, brand="Milbona"),
        off("4008400154329", "Gouda jung", 90, brand="Rewe", serving=(30.0, "1 Scheibe (30 g)")),
        off("5449000000996", "Cola", 40, per_unit="ml"),
    ]
    ranked = sorted(off_foods, key=lambda f: -f.popularity)
    ids = builder.write_foods(con, bls_foods, ranked)
    portions = [builder.Portion("F110100", "Stück (mittel)", 150.0), builder.Portion("NOPE", "x", 1.0)]
    count = builder.write_portions(con, portions, ids)
    builder.write_meta(con, {"build_date": "2026-10-13", "sample": "false"})
    builder.finish_database(con, schema)
    con.close()
    return path, count


def test_user_version_and_identity_hash_match_the_room_schema(database):
    path, _ = database
    expected = json.loads(SCHEMA_FILE.read_text())["database"]
    con = sqlite3.connect(path)
    assert con.execute("PRAGMA user_version").fetchone()[0] == expected["version"]
    assert con.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()[0] == expected["identityHash"]
    assert con.execute("PRAGMA integrity_check").fetchone()[0] == "ok"


def test_every_table_and_index_of_the_schema_exists(database):
    path, _ = database
    expected = json.loads(SCHEMA_FILE.read_text())["database"]
    con = sqlite3.connect(path)
    names = {r[0] for r in con.execute("SELECT name FROM sqlite_master")}
    for entity in expected["entities"]:
        assert entity["tableName"] in names
        for index in entity.get("indices", []):
            assert index["name"] in names


def test_ids_put_bls_first_then_off_by_popularity(database):
    path, _ = database
    con = sqlite3.connect(path)
    rows = con.execute("SELECT id, source, name FROM foods ORDER BY id").fetchall()
    assert [r[1] for r in rows] == ["BLS", "BLS", "OFF", "OFF", "OFF"]
    assert [r[2] for r in rows][2:] == ["Gouda jung", "Cola", "Käse Scheiben"]
    assert [r[0] for r in rows] == [1, 2, 3, 4, 5]


def test_search_finds_umlaut_spellings_with_prefixes(database):
    path, _ = database
    con = sqlite3.connect(path)
    for query in ("kase*", "kaese*".replace("ae", "a"), "kas*", "ka*"):
        hits = con.execute(
            "SELECT foods.name FROM foods JOIN foods_fts ON foods.id = foods_fts.docid WHERE foods_fts MATCH ?",
            (query,),
        ).fetchall()
        assert ("Käse Scheiben",) in hits, query


def test_search_with_several_words_needs_all_of_them(database):
    path, _ = database
    con = sqlite3.connect(path)
    sql = "SELECT foods.name FROM foods JOIN foods_fts ON foods.id = foods_fts.docid WHERE foods_fts MATCH ? ORDER BY foods.id"
    assert con.execute(sql, ("gouda* rewe*",)).fetchall() == [("Gouda jung",)]
    assert [r[0] for r in con.execute(sql, ("gouda*",)).fetchall()] == ["Gouda 48 % Fett i. Tr.", "Gouda jung"]


def test_unknown_values_are_null_not_zero(database):
    path, _ = database
    con = sqlite3.connect(path)
    protein, carbs, salt = con.execute("SELECT protein, carbs, salt FROM foods WHERE source_id = 'F110100'").fetchone()
    assert protein is None and carbs is None and salt is None


def test_off_fields_arrive_as_they_are(database):
    path, _ = database
    con = sqlite3.connect(path)
    row = con.execute("SELECT barcode, brand, per_unit, serving_g, serving_label, popularity FROM foods WHERE source_id = '4008400154329'").fetchone()
    assert row == ("4008400154329", "Rewe", "g", 30.0, "1 Scheibe (30 g)", 90)
    assert con.execute("SELECT per_unit FROM foods WHERE source_id = '5449000000996'").fetchone()[0] == "ml"


def test_portions_are_estimates_and_unknown_codes_are_skipped(database):
    path, count = database
    con = sqlite3.connect(path)
    assert count == 1
    assert con.execute("SELECT label, grams, estimated FROM food_portions").fetchall() == [("Stück (mittel)", 150.0, 1)]


def test_meta_is_written(database):
    path, _ = database
    con = sqlite3.connect(path)
    assert dict(con.execute("SELECT key, value FROM food_db_meta")) == {"build_date": "2026-10-13", "sample": "false"}


def test_sample_selection_keeps_every_food_with_a_portion_and_is_deterministic():
    bls_foods = [bls(f"C{i:04d}", f"Food {i}") for i in range(300)]
    off_ranked = [off(f"{4006381333931 + i}", f"Produkt {i}", 1000 - i) for i in range(50)]
    portions = [builder.Portion("C0007", "Stück", 10.0), builder.Portion("C0250", "Stück", 20.0)]
    first = builder.select_sample(bls_foods, off_ranked, portions, 90)
    again = builder.select_sample(bls_foods, off_ranked, portions, 90)
    assert [f.code for f in first[0]] == [f.code for f in again[0]]
    codes = {f.code for f in first[0]}
    assert {"C0007", "C0250"} <= codes
    assert len(first[0]) == 30  # a third of 90
    assert len(first[1]) == 50  # only 50 Open Food Facts foods exist
    assert first[1] == off_ranked[: len(first[1])]
    assert [f.code for f in first[0]] == sorted(codes)


def test_the_committed_sample_database_is_valid():
    path = builder.ROOT / "out" / "foods-sample.db"
    if not path.exists():
        pytest.skip("run build_food_db.py --sample 3000 first")
    expected = json.loads(SCHEMA_FILE.read_text())["database"]
    con = sqlite3.connect(path)
    assert con.execute("PRAGMA user_version").fetchone()[0] == expected["version"]
    assert con.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()[0] == expected["identityHash"]
    meta = dict(con.execute("SELECT key, value FROM food_db_meta"))
    assert meta["sample"] == "true"
    assert con.execute("SELECT COUNT(*) FROM foods").fetchone()[0] == int(meta["bls_count"]) + int(meta["off_count"])
    hits = con.execute(
        "SELECT COUNT(*) FROM foods JOIN foods_fts ON foods.id = foods_fts.docid WHERE foods_fts MATCH 'kase*'"
    ).fetchone()[0]
    assert hits > 0
