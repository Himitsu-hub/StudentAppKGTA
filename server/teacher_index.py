"""Fast inverted index: teacher surname → today's / week's lessons."""

from __future__ import annotations

import json
import threading
from pathlib import Path
from typing import Any, Dict, List, Optional

from teacher_match import parse_teacher_key, teachers_match

INDEX_PATH = Path("teacher_schedule_index.json")
_lock = threading.Lock()
_mem: Optional[dict] = None


def _surname_bucket(teacher_field: str) -> str:
    key = parse_teacher_key(teacher_field)
    if key and key[0]:
        return key[0]
    return (teacher_field or "").strip().lower()[:40]


def rebuild_teacher_index(db, week_type: str) -> dict:
    """Scan ScheduleRecord for week_type and write compact JSON index."""
    from database import ScheduleRecord

    by_surname: Dict[str, List[dict]] = {}
    records = (
        db.query(ScheduleRecord)
        .filter(ScheduleRecord.week_type == week_type)
        .all()
    )
    for rec in records:
        days = rec.schedule_json if isinstance(rec.schedule_json, list) else []
        for day_block in days:
            day_name = day_block.get("dayName") or day_block.get("day_name") or ""
            for lesson in day_block.get("lessons") or []:
                teacher = (lesson.get("teacher") or "").strip()
                if not teacher:
                    continue
                bucket = _surname_bucket(teacher)
                if not bucket:
                    continue
                item = {
                    "dayName": day_name,
                    "time": lesson.get("time") or "",
                    "subject": lesson.get("subject") or "",
                    "teacher": teacher,
                    "room": lesson.get("room") or "",
                    "type": lesson.get("type") or "",
                    "faculty": rec.faculty,
                    "course": rec.course,
                    "group": rec.group_name,
                    "subgroup": rec.subgroup or "",
                }
                by_surname.setdefault(bucket, []).append(item)

    payload = {
        "weekType": week_type,
        "surnames": by_surname,
        "lessonCount": sum(len(v) for v in by_surname.values()),
        "teacherCount": len(by_surname),
    }
    with _lock:
        INDEX_PATH.write_text(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        global _mem
        _mem = payload
    return {
        "weekType": week_type,
        "teachers": payload["teacherCount"],
        "lessons": payload["lessonCount"],
    }


def load_index(week_type: str) -> dict:
    global _mem
    with _lock:
        if _mem and _mem.get("weekType") == week_type:
            return _mem
        if INDEX_PATH.exists():
            try:
                data = json.loads(INDEX_PATH.read_text(encoding="utf-8"))
                if data.get("weekType") == week_type:
                    _mem = data
                    return data
            except Exception:
                pass
    return {"weekType": week_type, "surnames": {}}


def invalidate_index() -> None:
    global _mem
    with _lock:
        _mem = None
        try:
            if INDEX_PATH.exists():
                INDEX_PATH.unlink()
        except OSError:
            pass


def lookup_lessons(query: str, week_type: str, day_filter: Optional[set] = None) -> List[dict]:
    """
    Fast lookup using surname bucket + teachers_match.
    day_filter: set of day names or None for whole week.
    """
    data = load_index(week_type)
    surnames: Dict[str, List[dict]] = data.get("surnames") or {}
    q_key = parse_teacher_key(query)
    buckets: List[str] = []
    if q_key and q_key[0]:
        buckets.append(q_key[0])
    # Also try raw lower surname token
    raw = (query or "").strip().lower().split()
    if raw and raw[0] not in buckets:
        buckets.append(raw[0])

    candidates: List[dict] = []
    for b in buckets:
        candidates.extend(surnames.get(b, []))

    # Fallback: if no bucket hits (odd spelling), scan all — rare
    if not candidates and len(query.strip()) >= 4:
        for items in surnames.values():
            candidates.extend(items)

    merged: Dict[tuple, dict] = {}
    for lesson in candidates:
        teacher = lesson.get("teacher") or ""
        if not teachers_match(query, teacher):
            continue
        day_name = lesson.get("dayName") or ""
        if day_filter is not None and day_name not in day_filter:
            continue
        time_s = (lesson.get("time") or "").strip()
        subject_s = (lesson.get("subject") or "").strip()
        room_s = (lesson.get("room") or "").strip()
        type_s = (lesson.get("type") or "").strip()
        # Do not key by room — same pair may be "234" for one subgroup and "онлайн" for another.
        key = (day_name, time_s, subject_s.lower(), type_s.lower())
        group_label = lesson.get("group") or ""
        if lesson.get("subgroup"):
            group_label = f"{group_label} ({lesson['subgroup']})"
        if key not in merged:
            merged[key] = {
                "dayName": day_name,
                "time": time_s,
                "subject": subject_s,
                "teacher": teacher,
                "room": room_s,
                "type": type_s,
                "faculty": lesson.get("faculty") or "",
                "course": lesson.get("course") or 0,
                "group": group_label,
                "subgroup": "",
                "_groups": [group_label] if group_label else [],
                "_rooms": [room_s] if room_s else [],
            }
        else:
            item = merged[key]
            if group_label and group_label not in item["_groups"]:
                item["_groups"].append(group_label)
            if room_s and room_s not in item["_rooms"]:
                item["_rooms"].append(room_s)
            if len(teacher) > len(item.get("teacher") or ""):
                item["teacher"] = teacher

    out: List[dict] = []
    for item in merged.values():
        groups = sorted(item.pop("_groups"))
        rooms = item.pop("_rooms")
        item["group"] = ", ".join(groups)
        item["groups"] = groups
        # Prefer a single room; if mixed (cabinet + online) join them.
        if rooms:
            # keep order but unique already
            item["room"] = " / ".join(rooms) if len(rooms) > 1 else rooms[0]
        out.append(item)

    order = {
        "Понедельник": 0,
        "Вторник": 1,
        "Среда": 2,
        "Четверг": 3,
        "Пятница": 4,
        "Суббота": 5,
        "Воскресенье": 6,
    }
    out.sort(key=lambda x: (order.get(x["dayName"], 9), x.get("time") or "", x.get("subject") or ""))
    return out
