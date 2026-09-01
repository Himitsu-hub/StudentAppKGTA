"""Faculty / schedule-file registry for StudentApp."""

from __future__ import annotations

from pathlib import Path
from typing import Dict, List, Optional, Tuple

# faculty_id → metadata
FACULTIES: Dict[str, dict] = {
    "fae": {
        "id": "fae",
        "short": "АиЭ",
        "name": "Автоматика и электроника",
        "courses": [1, 2, 3, 4],
    },
    "mtf": {
        "id": "mtf",
        "short": "МТФ",
        "name": "Машиностроительный технологический факультет",
        "courses": [1, 2, 3, 4, 5],
    },
    "masters": {
        "id": "masters",
        "short": "Маг.",
        "name": "Магистратура (очное)",
        "courses": [2],
    },
}

DEFAULT_FACULTY = "fae"

# (faculty, course) → list of candidate filenames (first existing wins)
# MTF 4 and 5 share the same Excel; indexer filters groups by year suffix.
_FILE_CANDIDATES: Dict[Tuple[str, int], List[str]] = {
    # Prefer freshest АиЭ .xls (2026/27: 126/125/124/123). Old .xlsx often lagged a year.
    ("fae", 1): ["schedule_fae_1.xls", "schedule_fae_1.xlsx", "schedule1.xlsx", "schedule1.xls"],
    ("fae", 2): ["schedule_fae_2.xls", "schedule_fae_2.xlsx", "schedule2.xlsx", "schedule2.xls"],
    ("fae", 3): ["schedule_fae_3.xls", "schedule_fae_3.xlsx", "schedule3.xlsx", "schedule3.xls"],
    ("fae", 4): ["schedule_fae_4.xls", "schedule_fae_4.xlsx", "schedule4.xlsx", "schedule4.xls"],
    ("mtf", 1): ["schedule_mtf_1.xlsx", "schedule_mtf_1.xls"],
    ("mtf", 2): ["schedule_mtf_2.xlsx", "schedule_mtf_2.xls"],
    ("mtf", 3): ["schedule_mtf_3.xlsx", "schedule_mtf_3.xls"],
    ("mtf", 4): ["schedule_mtf_4_5.xlsx", "schedule_mtf_4_5.xls", "schedule_mtf_4.xlsx", "schedule_mtf_4.xls"],
    ("mtf", 5): ["schedule_mtf_4_5.xlsx", "schedule_mtf_4_5.xls", "schedule_mtf_5.xlsx", "schedule_mtf_5.xls"],
    ("masters", 2): [
        "schedule_masters_2.xlsx",
        "schedule_masters_2.xls",
        "schedule_mag_2.xlsx",
        "schedule_mag_2.xls",
    ],
}


def normalize_faculty(faculty: Optional[str]) -> str:
    fid = (faculty or DEFAULT_FACULTY).strip().lower()
    if fid not in FACULTIES:
        raise ValueError(f"Unknown faculty: {faculty}")
    return fid


def faculty_meta(faculty: Optional[str]) -> dict:
    return FACULTIES[normalize_faculty(faculty)]


def list_faculties() -> List[dict]:
    return [dict(v) for v in FACULTIES.values()]


def courses_for(faculty: Optional[str]) -> List[int]:
    return list(faculty_meta(faculty)["courses"])


def resolve_schedule_path(upload_dir: Path, faculty: str, course: int) -> Optional[Path]:
    fid = normalize_faculty(faculty)
    for name in _FILE_CANDIDATES.get((fid, course), []):
        path = upload_dir / name
        if path.exists():
            return path
    return None


def target_filename(faculty: str, course: int, suffix: str = ".xlsx") -> str:
    """Canonical upload name written by admin."""
    fid = normalize_faculty(faculty)
    if fid == "mtf" and course in (4, 5):
        return f"schedule_mtf_4_5{suffix}"
    if fid == "masters":
        return f"schedule_masters_{course}{suffix}"
    return f"schedule_{fid}_{course}{suffix}"


def needs_course_filter(faculty: str, course: int) -> bool:
    """True when one Excel serves multiple courses (МТФ 4–5)."""
    fid = normalize_faculty(faculty)
    return fid == "mtf" and course in (4, 5)


def admin_upload_slots(faculty: str) -> List[dict]:
    """
    Slots shown in /admin UI.
    MTF 4+5 share one Excel → one slot with course=4 (upload indexes 4 and 5).
    """
    fid = normalize_faculty(faculty)
    if fid == "fae":
        return [{"course": c, "label": f"{c} курс", "hint": ""} for c in (1, 2, 3, 4)]
    if fid == "mtf":
        return [
            {"course": 1, "label": "1 курс", "hint": ""},
            {"course": 2, "label": "2 курс", "hint": ""},
            {"course": 3, "label": "3 курс", "hint": ""},
            {
                "course": 4,
                "label": "4–5 курс",
                "hint": "Один Excel на 4 и 5 курс (как в файле МТФ 4-5 К)",
            },
        ]
    if fid == "masters":
        return [
            {
                "course": 2,
                "label": "2 курс (очное)",
                "hint": "Файл магистратуры. Парсер сетки пока экспериментальный.",
            }
        ]
    return [{"course": c, "label": f"{c} курс", "hint": ""} for c in courses_for(fid)]
