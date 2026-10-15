import pytest

from off_source import (
    STEP_BARCODE, STEP_DEDUPE, STEP_ENERGY, STEP_KJ, STEP_MACROS, STEP_NAME, STEP_SANITY,
    FilterReport, RawOffRow, convert, convert_all, per_unit_of, pick_brand, pick_name, pick_serving,
    rank_by_popularity,
)

GOOD = dict(
    code="4006381333931", name_csv="Haferflocken", kcal="372", kj="1560", fat="7", carbs="58", sugar="1",
    fiber="10", protein="13", salt="0.02", brands="Kölln, Peter Kölln", quantity="500 g", unique_scans="12",
    last_modified="100", completeness="0.8",
)


def raw(**overrides):
    return RawOffRow(**{**GOOD, **overrides})


def run(**overrides):
    report = FilterReport()
    report.start_rows = 1
    return convert(raw(**overrides), report), report


def test_a_good_row_is_converted():
    food, report = run()
    assert food is not None and report.kept == 1
    assert (food.barcode, food.name, food.brand) == ("4006381333931", "Haferflocken", "Kölln")
    assert food.search_text == "haferflocken kolln peter kolln"
    assert (food.kcal, food.protein, food.carbs, food.sugar, food.fat, food.fiber, food.salt) == (372, 13, 58, 1, 7, 10, 0.02)
    assert (food.per_unit, food.popularity) == ("g", 12)


@pytest.mark.parametrize("code", ["4006381333932", "123", "abc", "", "400638133393", "0000000000000"])
def test_invalid_barcodes_are_dropped(code):
    food, report = run(code=code)
    assert food is None and report.rejected[STEP_BARCODE] == 1


def test_a_valid_8_digit_barcode_is_kept():
    food, _ = run(code="40084107")
    assert food is not None and food.barcode == "40084107"


@pytest.mark.parametrize("name_csv, name_de", [(None, None), ("", None), ("   ", ""), ("-", None), ("123 456", None)])
def test_rows_without_a_usable_name_are_dropped(name_csv, name_de):
    food, report = run(name_csv=name_csv, name_de=name_de)
    assert food is None and report.rejected[STEP_NAME] == 1


def test_the_german_name_wins_over_the_main_language_name():
    assert pick_name(raw(name_csv="Past Lait entier", name_de="Past Vollmilch")) == "Past Vollmilch"
    assert pick_name(raw(name_csv="Haferflocken", name_de=None)) == "Haferflocken"
    assert pick_name(raw(name_csv="Haferflocken", name_de="  ")) == "Haferflocken"


def test_names_are_cleaned_and_shortened():
    assert pick_name(raw(name_csv="  Bio   Haferflocken \n zart ")) == "Bio Haferflocken zart"
    long_name = pick_name(raw(name_csv="Wort " * 100))
    assert long_name is not None and len(long_name) == 160 and long_name.endswith("…")


def test_energy_falls_back_to_kilojoules():
    food, _ = run(kcal=None, kj="1046")
    assert food is not None and food.kcal == pytest.approx(250.0)


def test_rows_without_any_energy_are_dropped():
    food, report = run(kcal=None, kj=None)
    assert food is None and report.rejected[STEP_ENERGY] == 1
    food, report = run(kcal="abc", kj="")
    assert food is None and report.rejected[STEP_ENERGY] == 1


@pytest.mark.parametrize(
    "overrides",
    [
        {"kcal": "901", "kj": None},
        {"kcal": "-1", "kj": None},
        {"fat": "101"},
        {"carbs": "-0.1"},
        {"protein": "100.5"},
        {"sugar": "150"},
        {"fiber": "101"},
        {"salt": "101"},
        {"protein": "40", "carbs": "40", "fat": "30", "kcal": "400", "kj": None},  # adds up to 110 g
    ],
)
def test_insane_values_are_dropped(overrides):
    food, report = run(**overrides)
    assert food is None and report.rejected[STEP_SANITY] == 1


def test_exactly_at_the_limits_is_fine():
    food, _ = run(kcal="900", kj=None, fat="100", carbs="0", protein="0", sugar="0", fiber="0", salt="0")
    assert food is not None
    food, _ = run(kcal="0", kj=None, fat="0", carbs="0", protein="0", sugar="0", fiber="0", salt="0")
    assert food is not None


def test_a_kilojoule_value_typed_into_the_kcal_field_is_dropped():
    food, report = run(kcal="1560", kj="1560")  # 1560 kcal would also be insane; use a sane-looking pair
    assert food is None
    food, report = run(kcal="290", kj="282", protein="13", carbs="58", fat="7")
    assert food is None and report.rejected[STEP_KJ] == 1


def test_small_differences_between_kcal_and_kj_are_fine():
    food, _ = run(kcal="372", kj="1550")
    assert food is not None


def test_egregious_energy_macro_mismatches_are_dropped():
    food, report = run(kcal="46", kj=None, protein="0.5", carbs="11", fat="11")  # macros say 145 kcal
    assert food is None and report.rejected[STEP_MACROS] == 1


def test_leeway_for_alcohol_fibre_and_rounding():
    # 4*0 + 4*0 + 9*0 = 0, but 40 g alcohol explain 280 kcal
    food, _ = run(kcal="280", kj=None, protein="0", carbs="0", fat="0", sugar=None, fiber=None, salt=None, alcohol="40")
    assert food is not None
    # polyols: macros say 360, label says 240 -> within 50 % of the larger value? 120 > 80 but 120 < 180
    food, _ = run(kcal="240", kj=None, protein="0", carbs="90", fat="0", sugar=None, fiber=None, salt=None)
    assert food is not None


def test_missing_macros_are_not_judged_and_stay_unknown():
    food, _ = run(protein=None, carbs=None, fat=None, sugar=None, fiber=None, salt=None, sodium=None)
    assert food is not None
    assert food.protein is None and food.carbs is None and food.fat is None and food.salt is None


def test_salt_comes_from_sodium_when_missing():
    food, _ = run(salt=None, sodium="0.4")
    assert food is not None and food.salt == pytest.approx(1.0)
    food, _ = run(salt="0.5", sodium="0.4")
    assert food is not None and food.salt == 0.5


@pytest.mark.parametrize(
    "quantity, expected",
    [("500 ml", "ml"), ("1 l", "ml"), ("1,5 L", "ml"), ("330ml", "ml"), ("25 cl", "ml"), ("6 x 0.33 l", "ml"),
     ("500 g", "g"), ("1 kg", "g"), (None, "g"), ("", "g"), ("12 pcs", "g"), ("250g", "g")],
)
def test_drinks_are_per_100_ml(quantity, expected):
    assert per_unit_of(quantity) == expected


def test_brand_is_the_first_of_the_list():
    assert pick_brand("Dittmann, Dittmann Feinkost") == ("Dittmann", "Dittmann Dittmann Feinkost")
    assert pick_brand(None) == (None, None)
    assert pick_brand(" , ") == (None, None)


def test_servings():
    assert pick_serving(raw(serving_quantity="30", serving_size="1 Riegel (30 g)")) == (30.0, "1 Riegel (30 g)")
    assert pick_serving(raw(serving_quantity="0", serving_size="x")) == (None, None)
    assert pick_serving(raw(serving_quantity="99999", serving_size="x")) == (None, None)
    assert pick_serving(raw(serving_quantity=None, serving_size="1 portion")) == (None, None)


def test_only_the_first_failing_filter_is_counted():
    food, report = run(code="1", name_csv=None, kcal=None)
    assert food is None
    assert report.rejected[STEP_BARCODE] == 1 and report.rejected[STEP_NAME] == 0 and report.rejected[STEP_ENERGY] == 0


def test_one_row_per_barcode_and_the_newest_wins():
    report = FilterReport()
    rows = [
        raw(code="4006381333931", name_csv="Alt", last_modified="100"),
        raw(code="4006381333931", name_csv="Neu", last_modified="200"),
        raw(code="0004006381333931", name_csv="Anders geschrieben", last_modified="50"),
    ]
    # the third code is a 16-digit spelling and invalid; use the valid 14-digit spelling instead
    rows[2] = raw(code="04006381333931", name_csv="Gleiche Ware", last_modified="150")
    foods = convert_all(rows, report)
    assert [f.name for f in foods] == ["Neu"]
    assert report.rejected[STEP_DEDUPE] == 2
    assert report.start_rows == 3


def test_the_report_adds_up():
    report = FilterReport(start_label="OFF")
    foods = convert_all([raw(), raw(code="1"), raw(name_csv=None), raw(kcal=None, kj=None)], report)
    assert len(foods) == 1 == report.kept
    assert sum(report.rejected.values()) == 3
    assert report.lines()[0] == "OFF: 4"
    assert report.lines()[-1] == "kept: 1"


def test_ranking_is_by_popularity_then_short_names_then_barcode():
    report = FilterReport()
    foods = convert_all(
        [
            raw(code="4006381333931", name_csv="B lang genug", unique_scans="5"),
            raw(code="4008400154329", name_csv="A", unique_scans="5"),
            raw(code="40084107", name_csv="Z", unique_scans="50"),
        ],
        report,
    )
    assert [f.name for f in rank_by_popularity(foods)] == ["Z", "A", "B lang genug"]


# --- review findings (Phase 4) ---------------------------------------------------------------------


def test_html_entities_of_open_food_facts_are_decoded_in_names_brands_and_search_text():
    food, _ = run(
        name_csv="Chips &quot;fein gesalzen&quot; &amp; Co. -&gt; Nudeln", brands="Ben &amp; Jerry&#039;s, Patak&#039;s"
    )
    assert food is not None
    assert food.name == 'Chips "fein gesalzen" & Co. -> Nudeln'
    assert (food.brand, food.search_text) == ("Ben & Jerry's", "chips fein gesalzen co nudeln ben jerry s patak s")
    assert not {"quot", "amp", "039", "gt"} & set(food.search_text.split())


@pytest.mark.parametrize(
    "overrides",
    [
        {"kcal": "0", "kj": None, "protein": None, "carbs": None, "fat": None},  # honey with an empty default
        {"kcal": "0", "kj": "0", "protein": None, "carbs": None, "fat": None},
        {"kcal": None, "kj": "0", "protein": None, "carbs": None, "fat": None, "sugar": "3"},
    ],
)
def test_zero_kcal_without_any_macro_is_a_placeholder_not_a_measurement(overrides):
    food, report = run(**overrides)
    assert food is None and report.rejected[STEP_ENERGY] == 1


def test_zero_kcal_with_a_stated_macro_is_a_real_zero():
    food, _ = run(kcal="0", kj=None, protein=None, carbs="0", fat=None, sugar=None, fiber=None, salt=None)
    assert food is not None and food.kcal == 0
    food, _ = run(kcal="0", kj=None, protein="0", carbs=None, fat=None, sugar=None, fiber=None, salt=None)
    assert food is not None


@pytest.mark.parametrize(
    "overrides, accepted",
    [
        ({"protein": "35", "carbs": "40", "fat": "30", "kcal": "400", "kj": None}, True),  # exactly 105 g
        ({"protein": "35.1", "carbs": "40", "fat": "30", "kcal": "400", "kj": None}, False),  # 105.1 g
        ({"protein": "0", "carbs": "0", "fat": "100", "kcal": "900", "kj": None}, True),
        ({"kcal": "900.1", "kj": None}, False),
        ({"fat": "100", "carbs": "0", "protein": "0", "sugar": "0", "fiber": "0", "salt": "100", "kcal": "900", "kj": None}, True),
        ({"salt": "100.1"}, False),
    ],
)
def test_the_sanity_limits_hold_exactly_at_their_boundaries(overrides, accepted):
    food, _ = run(**overrides)
    assert (food is not None) is accepted


NO_MACROS = dict(protein=None, carbs=None, fat=None, sugar=None, fiber=None, salt=None)


@pytest.mark.parametrize(
    "kcal, kj, accepted",
    [
        ("300", "1255", True),   # 1255 kJ = 299.96 kcal: they agree
        ("350", "1255", True),   # 50.04 kcal apart (> 50) but only 14 % of the larger value (< 30 %)
        ("100", "1255", False),  # 199.96 kcal apart and 67 %
        ("300", "1760", True),   # 1760 kJ = 420.7 kcal: 120.7 apart, 28.7 % of 420.7 (< 30 %)
        ("300", "1800", False),  # 1800 kJ = 430.2 kcal: 130.2 apart, 30.3 % of 430.2 (> 30 %)
        ("20", "230", True),     # 20 vs 55 kcal: 35 apart, under the 50 kcal floor however large the share
    ],
)
def test_kcal_and_kj_boundaries(kcal, kj, accepted):
    # A contradiction needs BOTH: more than 50 kcal apart and more than 30 % of the larger value.
    food, report = run(kcal=kcal, kj=kj, **NO_MACROS)
    assert (food is not None) is accepted
    assert report.rejected[STEP_KJ] == (0 if accepted else 1)


@pytest.mark.parametrize(
    "kcal, protein, carbs, fat, accepted",
    [
        ("0", "20", "0", "0", True),      # macros say 80, label 0: 80 apart is not more than 80
        ("0", "20.5", "0", "0", False),   # macros say 82: more than 80 and 100 %
        ("80", "0", "0", "0", True),      # label 80, macros 0: 80 apart
        ("100", "0", "0", "0", False),    # 100 apart and 100 % of 100
        ("200", "0", "90", "0", True),    # polyols: macros say 360, 160 apart, but only 44 % of 360 (< 50 %)
        ("170", "0", "90", "0", False),   # 190 apart and 52.8 % of 360
    ],
)
def test_kcal_versus_macro_boundaries(kcal, protein, carbs, fat, accepted):
    # Mismatch counts only when more than 80 kcal AND more than 50 % of the larger value.
    food, report = run(kcal=kcal, kj=None, protein=protein, carbs=carbs, fat=fat, sugar=None, fiber=None, salt=None)
    assert (food is not None) is accepted
    assert report.rejected[STEP_MACROS] == (0 if accepted else 1)


@pytest.mark.parametrize(
    "quantity, expected",
    [("500 g / 1000 ml", "g"), ("300 g / 500 ml", "g"), ("720 ml - 504 g", "ml"), ("400 g (ca. 2 l)", "g"),
     ("12 fl oz", "ml"), ("16 oz", "g")],
)
def test_the_first_figure_of_a_mixed_quantity_decides_the_unit(quantity, expected):
    assert per_unit_of(quantity) == expected


def test_a_pack_over_a_kilogram_is_no_portion_but_the_food_stays():
    assert pick_serving(raw(serving_quantity="1000", serving_size="1 Dose")) == (1000.0, "1 Dose")
    assert pick_serving(raw(serving_quantity="1000.1", serving_size="1 Pack")) == (None, None)
    assert pick_serving(raw(serving_quantity="3200", serving_size="1 Glas (3200 g)")) == (None, None)
    food, report = run(serving_quantity="3200", serving_size="1 Glas")
    assert food is not None and food.serving_g is None and food.serving_label is None and report.kept == 1


def test_entities_that_were_escaped_twice_are_decoded_too():
    food, _ = run(name_csv="Wavy Blends Garlic &amp;amp; Herbs", brands="Gouda&amp;#039;s Glorie")
    assert food is not None
    assert (food.name, food.brand) == ("Wavy Blends Garlic & Herbs", "Gouda's Glorie")
    assert food.search_text == "wavy blends garlic herbs gouda s glorie"
