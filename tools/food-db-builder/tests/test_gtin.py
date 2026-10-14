import pytest

from gtin import barcode_key, is_valid_gtin


@pytest.mark.parametrize(
    "code",
    [
        "4006381333931",  # EAN-13 (Stabilo)
        "4008400154329",  # EAN-13
        "40084107",  # EAN-8
        "012345678905",  # UPC-A
        "00012345678905",  # GTIN-14
        "0000101209159",  # EAN-13 with leading zeros
    ],
)
def test_valid_codes(code):
    assert is_valid_gtin(code)


@pytest.mark.parametrize(
    "code",
    [
        "4006381333932",  # wrong check digit
        "40084108",  # wrong check digit
        "123",  # too short
        "400638133393",  # 12 digits but wrong check digit
        "40063813339311",  # 14 digits, wrong
        "400638133393a",  # not digits
        "",
        "0000000000000",  # all zeros
        "00000000",
        "4006381333931 ",  # whitespace
        "４００６３８１３３３９３１",  # full-width digits are not digits for us
    ],
)
def test_invalid_codes(code):
    assert not is_valid_gtin(code)


def test_the_same_product_has_the_same_key_in_every_spelling():
    assert barcode_key("012345678905") == barcode_key("0012345678905") == barcode_key("00012345678905")
    assert barcode_key("40084107") != barcode_key("4008410")
