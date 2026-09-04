"""Normalize and match teacher names between Excel schedule and teachers.json."""

from __future__ import annotations

import re
from typing import Optional, Tuple

_TITLE_RE = re.compile(
    r"(?i)\b("
    r"д\.?\s*т\.?\s*н\.?|к\.?\s*т\.?\s*н\.?|к\.?\s*п\.?\s*н\.?|к\.?\s*пх\.?\s*н\.?|"
    r"к\.?\s*и\.?\s*н\.?|к\.?\s*ф\.?\s*н\.?|к\.?\s*э\.?\s*н\.?|"
    r"д\.?\s*п\.?\s*н\.?|д\.?\s*ф\.?\s*н\.?|"
    r"ст\.?\s*преп\.?|старший\s+преподаватель|преподаватель|"
    r"доцент|профессор|асс\.?|ассистент|"
    r"зав\.?\s*каф\.?|заведующий"
    r")\b\.?"
)

_INITIALS_RE = re.compile(
    r"^([А-ЯЁA-Z][а-яёa-z\-]+)\s+([А-ЯЁA-Z])\.?\s*([А-ЯЁA-Z])?\.?\s*$"
)
_FULL_RE = re.compile(
    r"^([А-ЯЁA-Z][а-яёa-z\-]+)\s+([А-ЯЁA-Z][а-яёa-z\-]+)\s+([А-ЯЁA-Z][а-яёa-z\-]+)"
)


def strip_titles(raw: str) -> str:
    s = (raw or "").replace("\n", " ")
    s = _TITLE_RE.sub(" ", s)
    s = re.sub(r"\s+", " ", s).strip(" ,.;")
    return s


def _fold_yo(s: str) -> str:
    """Normalize ё/Ё → е/Е so Шварёва and Шварева hit the same bucket."""
    return (s or "").replace("ё", "е").replace("Ё", "Е")


def parse_teacher_key(raw: str) -> Optional[Tuple[str, str, str]]:
    """
    Return (surname_lower, initial1_lower, initial2_lower).
    Works for 'Зяблицева О.В.' and 'Зяблицева Ольга Витальевна'.
    """
    s = strip_titles(raw)
    if not s:
        return None
    # Take first person if comma-separated
    s = s.split(",")[0].strip()
    m = _FULL_RE.match(s)
    if m:
        return (
            _fold_yo(m.group(1).lower()),
            m.group(2)[0].lower(),
            m.group(3)[0].lower(),
        )
    m = _INITIALS_RE.match(s)
    if m:
        i2 = (m.group(3) or "").lower()
        return _fold_yo(m.group(1).lower()), m.group(2).lower(), i2
    # Surname only
    parts = s.split()
    if parts:
        return _fold_yo(parts[0].lower()), "", ""
    return None


def teachers_match(query: str, candidate: str) -> bool:
    """True if schedule teacher string matches search query / directory FIO."""
    q = parse_teacher_key(query)
    c = parse_teacher_key(candidate)
    if not q or not c:
        # fallback: loose substring on stripped text
        qs = _fold_yo(strip_titles(query).lower())
        cs = _fold_yo(strip_titles(candidate).lower())
        return bool(qs) and qs in cs
    if q[0] != c[0]:
        return False
    # If query has initials, require them; if only surname — surname match is enough
    if q[1] and c[1] and q[1] != c[1]:
        return False
    if q[2] and c[2] and q[2] != c[2]:
        return False
    return True
