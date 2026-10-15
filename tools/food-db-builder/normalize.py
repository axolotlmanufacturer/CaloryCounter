"""Search text normalization (PROMPT.md section 5.2).

Must stay identical to ``TextNormalizer.normalize`` in ``:domain`` (Kotlin): the builder applies it to the
stored text, the app applies it to what the user types. Both are tested against ``normalize_cases.json``.

Steps: lowercase -> ``ß`` -> ``ss``, ``ä``/``ae`` -> ``a``, ``ö``/``oe`` -> ``o``, ``ü``/``ue`` -> ``u``
-> strip remaining diacritics -> everything except ``a-z`` and ``0-9`` becomes a space -> collapse and trim.
"""

from __future__ import annotations

import re
import unicodedata

_NOT_ALPHANUMERIC = re.compile(r"[^a-z0-9]+")


def normalize(text: str) -> str:
    folded = (
        text.lower()
        .replace("ß", "ss")
        .replace("ä", "a")
        .replace("ö", "o")
        .replace("ü", "u")
        .replace("ae", "a")
        .replace("oe", "o")
        .replace("ue", "u")
    )
    decomposed = unicodedata.normalize("NFD", folded)
    without_marks = "".join(c for c in decomposed if not unicodedata.category(c).startswith("M"))
    return _NOT_ALPHANUMERIC.sub(" ", without_marks).strip()
