"""Reading the real file formats: tab separated CSV with countries, tab separated mobile dump with names."""

import gzip
from pathlib import Path

from off_source import FilterReport, convert_all, read_off

CSV_HEADER = [
    "code", "product_name", "brands", "quantity", "serving_size", "serving_quantity", "last_modified_t",
    "unique_scans_n", "completeness", "countries_tags", "energy-kcal_100g", "energy-kj_100g", "fat_100g",
    "carbohydrates_100g", "sugars_100g", "fiber_100g", "proteins_100g", "salt_100g", "sodium_100g", "alcohol_100g",
    "url", "creator",
]


def csv_row(code, name, countries, kcal, **extra):
    values = dict.fromkeys(CSV_HEADER, "")
    values.update(code=code, product_name=name, brands="Marke", quantity="500 g", last_modified_t="100",
                  unique_scans_n="3", completeness="0.5", countries_tags=countries, **{"energy-kcal_100g": kcal},
                  fat_100g="7", carbohydrates_100g="58", proteins_100g="13")
    values.update(extra)
    return "\t".join(values[c] for c in CSV_HEADER)


def write_gz(path: Path, lines: list[str]) -> Path:
    with gzip.open(path, "wt", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")
    return path


def test_reads_germany_and_takes_german_names_from_the_mobile_dump(tmp_path):
    csv = write_gz(
        tmp_path / "off.csv.gz",
        [
            "\t".join(CSV_HEADER),
            csv_row("4006381333931", "Oat flakes", "en:france,en:germany", "372"),
            csv_row("4008400154329", "Only french", "en:france", "100"),
            csv_row("5449000000996", "Cola \"Zero\"", "en:germany", "0", fat_100g="0", carbohydrates_100g="0", proteins_100g="0"),
            csv_row("40084107", "", "en:germany", "250"),
        ],
    )
    mobile = write_gz(
        tmp_path / "mobile.tsv.gz",
        [
            "code\tproduct_name\tquantity\tbrands",
            "4006381333931\t[{'lang': main, 'text': Oat flakes}, {'lang': de, 'text': Haferflocken, zart}, {'lang': fr, 'text': Flocons}]\t500 g\tMarke",
            "40084107\t[{'lang': main, 'text': Quark}, {'lang': de, 'text': Magerquark}]\t250 g\tMarke",
            "5449000000996\t[{'lang': main, 'text': Cola}, {'lang': en, 'text': Cola}]\t\t",
        ],
    )
    report = FilterReport()
    rows = list(read_off(csv, mobile, report))
    assert sorted(r.code for r in rows) == ["4006381333931", "40084107", "5449000000996"]
    assert "of 4 in the CSV" in report.start_label

    by_code = {r.code: r for r in rows}
    assert by_code["4006381333931"].name_de == "Haferflocken, zart"
    assert by_code["4006381333931"].name_csv == "Oat flakes"
    assert by_code["40084107"].name_de == "Magerquark"
    assert by_code["5449000000996"].name_de is None  # no German entry: the CSV name is the fallback
    assert by_code["5449000000996"].name_csv == 'Cola "Zero"'  # quotes are data, not CSV syntax

    foods = convert_all(rows, report)
    assert {f.name for f in foods} == {"Haferflocken, zart", "Magerquark", 'Cola "Zero"'}


def test_works_without_the_mobile_dump(tmp_path):
    csv = write_gz(tmp_path / "off.csv.gz", ["\t".join(CSV_HEADER), csv_row("4006381333931", "Oat flakes", "en:germany", "372")])
    rows = list(read_off(csv, None, FilterReport()))
    assert [(r.code, r.name_de, r.name_csv) for r in rows] == [("4006381333931", None, "Oat flakes")]
