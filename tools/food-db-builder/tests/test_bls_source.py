import openpyxl
import pytest

from bls_source import find_columns, parse_cell, read_bls

HEADER = [
    "BLS Code", "Lebensmittelbezeichnung", "Food name",
    "ENERCC Energie (Kilokalorien) [kcal/100g]", "ENERCC Datenherkunft", "ENERCC Referenz",
    "PROT625 Protein (Nx6,25) [g/100g]", "PROT625 Datenherkunft", "PROT625 Referenz",
    "FAT Fett [g/100g]", "FAT Datenherkunft", "FAT Referenz",
    "CHO Kohlenhydrate, verfügbar [g/100g]", "CHO Datenherkunft", "CHO Referenz",
    "SUGAR Zucker (Mono- und Disaccharide), gesamt [g/100g]", "SUGAR Datenherkunft", "SUGAR Referenz",
    "FIBT Ballaststoffe, gesamt [g/100g]", "FIBT Datenherkunft", "FIBT Referenz",
    "NACL Salz (Natriumchlorid) [g/100g]", "NACL Datenherkunft", "NACL Referenz",
    "NA Natrium [mg/100g]", "NA Datenherkunft", "NA Referenz",
]


def row(code, name, kcal, protein, fat, cho, sugar, fibre, nacl, na):
    cells = {"code": code, "name": name}
    values = [code, name, None]
    for value in (kcal, protein, fat, cho, sugar, fibre, nacl, na):
        values += [value, "Analyse", "-"]
    return values


@pytest.fixture
def workbook(tmp_path):
    def make(*rows):
        path = tmp_path / "bls.xlsx"
        book = openpyxl.Workbook()
        sheet = book.active
        sheet.append(HEADER)
        for r in rows:
            sheet.append(r)
        book.save(path)
        return path

    return make


@pytest.mark.parametrize(
    "cell, expected",
    [(12, 12.0), (3.5, 3.5), ("-", None), (None, None), ("", None), ("TR", 0.0), ("<LOD", 0.0), ("<LOQ", 0.0),
     ("<LOD or <LOQ", 0.0), ("0,5", 0.5), ("abc", None), (True, None)],
)
def test_parse_cell(cell, expected):
    assert parse_cell(cell) == expected


def test_maps_the_nutrients(workbook):
    path = workbook(row("F110100", "Apfel roh", 58, 0.3, 0.6, 12.4, 11.4, 2.0, 0.0, 2))
    (food,) = read_bls(path)
    assert (food.code, food.name, food.search_text) == ("F110100", "Apfel roh", "apfel roh")
    assert food.kcal == 58.0
    assert (food.protein, food.fat, food.carbs, food.sugar, food.fiber) == (0.3, 0.6, 12.4, 11.4, 2.0)
    assert food.salt == 0.0


def test_not_determined_stays_unknown_and_trace_is_zero(workbook):
    path = workbook(row("X1", "Etwas", 100, "TR", "-", 10, "-", "<LOD", 1.0, 400))
    (food,) = read_bls(path)
    assert food.protein == 0.0
    assert food.fat is None
    assert food.sugar is None
    assert food.fiber == 0.0


def test_salt_comes_from_sodium_only_when_salt_is_missing(workbook):
    path = workbook(
        row("A", "Mit Salz", 10, 1, 1, 1, 1, 1, 1.5, 400),
        row("B", "Nur Natrium", 10, 1, 1, 1, 1, 1, "-", 400),
        row("C", "Nichts", 10, 1, 1, 1, 1, 1, "-", "-"),
    )
    foods = {f.code: f for f in read_bls(path)}
    assert foods["A"].salt == 1.5
    assert foods["B"].salt == pytest.approx(1.0)  # 400 mg sodium x 2.5 = 1 g salt
    assert foods["C"].salt is None


def test_rows_without_name_or_energy_are_skipped(workbook):
    path = workbook(
        row("A", "Gut", 10, 1, 1, 1, 1, 1, 0, 0),
        row("B", None, 10, 1, 1, 1, 1, 1, 0, 0),
        row("C", "Ohne Energie", "-", 1, 1, 1, 1, 1, 0, 0),
        row(None, "Ohne Code", 10, 1, 1, 1, 1, 1, 0, 0),
    )
    assert [f.code for f in read_bls(path)] == ["A"]


def test_names_are_cleaned_and_normalized(workbook):
    path = workbook(row("A", "  Käse,   Gouda  ", 10, 1, 1, 1, 1, 1, 0, 0))
    (food,) = read_bls(path)
    assert food.name == "Käse, Gouda"
    assert food.search_text == "kase gouda"


def test_a_table_without_the_needed_columns_is_an_error():
    with pytest.raises(ValueError, match="missing nutrient columns"):
        find_columns(("BLS Code", "Lebensmittelbezeichnung", "ENERCC Energie [kcal/100g]"))


def test_a_salt_of_zero_where_sodium_is_not_determined_is_unknown(workbook):
    # BLS writes 0 into NACL when it could not compute it from sodium (NA is "-"): that is "unknown", not "salt-free".
    path = workbook(
        row("A", "Berechnet ohne Natrium", 10, 1, 1, 1, 1, 1, 0, "-"),
        row("B", "Natrium bestimmt, Salz null", 10, 1, 1, 1, 1, 1, 0, 0),
        row("C", "Salz gemessen", 10, 1, 1, 1, 1, 1, 0.4, "-"),
    )
    foods = {f.code: f for f in read_bls(path)}
    assert foods["A"].salt is None
    assert foods["B"].salt == 0.0
    assert foods["C"].salt == 0.4
