"""The normalization must agree with the shared vectors (also tested in Kotlin) and be stable."""

import json
from pathlib import Path

import pytest

from normalize import normalize

CASES = json.loads((Path(__file__).parent.parent / "normalize_cases.json").read_text(encoding="utf-8"))["cases"]


@pytest.mark.parametrize("case", CASES, ids=[repr(c["input"]) for c in CASES])
def test_shared_vectors(case):
    assert normalize(case["input"]) == case["expected"]


def test_umlaut_spellings_meet():
    assert {normalize(s) for s in ("Käse", "Kaese", "kase", "KÄSE")} == {"kase"}


@pytest.mark.parametrize("case", CASES, ids=[repr(c["input"]) for c in CASES])
def test_normalizing_twice_changes_nothing(case):
    once = normalize(case["input"])
    assert normalize(once) == once
