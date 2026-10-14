"""Barcode (GTIN) helpers: check digits and the key used to find the same product under different spellings."""

from __future__ import annotations

VALID_LENGTHS = (8, 12, 13, 14)


def is_valid_gtin(code: str) -> bool:
    """8, 12, 13 or 14 digits with a correct check digit (and not all zeros)."""
    if not code.isascii() or not code.isdigit() or len(code) not in VALID_LENGTHS:
        return False
    if barcode_key(code) == "":
        return False
    body, check = code[:-1], int(code[-1])
    total = sum(int(d) * (3 if i % 2 == 0 else 1) for i, d in enumerate(reversed(body)))
    return (10 - total % 10) % 10 == check


def barcode_key(code: str) -> str:
    """The same product has the same key whether it is written as EAN-8, UPC-A (12) or EAN-13: the digits without leading zeros."""
    return code.lstrip("0")
