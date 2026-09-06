import os
import io
import hmac
import json
import shutil
import tempfile
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session

from database import (
    GroupsCache,
    ScheduleRecord,
    UploadLog,
    get_db,
    init_db,
)
from faculties import (
    DEFAULT_FACULTY,
    courses_for,
    faculty_meta,
    list_faculties,
    needs_course_filter,
    normalize_faculty,
    resolve_schedule_path,
    target_filename,
)
from parser import (
    get_current_week_type,
    get_groups_from_file,
    get_today_name,
    infer_course_from_group,
    parse_schedule_for_group,
    schedule_to_dict,
)
from schedule_validator import validate_schedule_file
from teacher_index import (
    invalidate_index,
    load_index,
    lookup_lessons,
    rebuild_teacher_index,
)
from teacher_match import teachers_match

load_dotenv()

ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "").strip()
if not ADMIN_PASSWORD:
    # Dev fallback only — production must set ADMIN_PASSWORD in .env
    ADMIN_PASSWORD = "changeme-dev-only"
    print("WARNING: ADMIN_PASSWORD is not set. Using insecure default.")

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
STATIC_DIR = Path("static")
STATIC_DIR.mkdir(exist_ok=True)
TEACHERS_FILE = Path("teachers.json")
WEEK_TYPES = ("Числитель", "Знаменатель")

app = FastAPI(
    title="StudentApp Schedule API",
    version="2.0.0",
    description="API расписания, новостей и преподавателей КГТА",
)

if STATIC_DIR.is_dir():
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")


@app.get("/favicon.ico", include_in_schema=False)
def favicon():
    """Browser tab icon for admin and root."""
    png = STATIC_DIR / "favicon.png"
    ico = STATIC_DIR / "favicon.ico"
    if ico.exists():
        return FileResponse(ico, media_type="image/x-icon")
    if png.exists():
        return FileResponse(png, media_type="image/png")
    raise HTTPException(status_code=404, detail="favicon missing")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


def verify_admin_password(password: str) -> None:
    """
    Constant-time password check.
    Use UTF-8 bytes — secrets.compare_digest(str, str) crashes on non-ASCII
    (TypeError → bare "Internal Server Error" in the admin UI).
    """
    expected = (ADMIN_PASSWORD or "").encode("utf-8")
    provided = (password or "").encode("utf-8")
    if not expected or not hmac.compare_digest(provided, expected):
        raise HTTPException(status_code=401, detail="Invalid admin password")


def schedule_days_from_json(raw: list) -> list[dict]:
    return raw if isinstance(raw, list) else []


def _filter_groups_for_course(
    faculty: str, course: int, groups: dict
) -> dict:
    """For shared Excel (МТФ 4–5) keep only groups belonging to this course year."""
    if not needs_course_filter(faculty, course):
        return groups
    filtered = {}
    for name, subs in groups.items():
        inferred = infer_course_from_group(name)
        if inferred == course:
            filtered[name] = subs
    return filtered


def index_course_file(
    db: Session,
    course: int,
    filename: str = "",
    faculty: str = DEFAULT_FACULTY,
) -> tuple[int, int]:
    """Parse Excel for a faculty+course and cache all groups × week types in SQLite."""
    faculty = normalize_faculty(faculty)
    file_path = resolve_schedule_path(UPLOAD_DIR, faculty, course)
    if file_path is None:
        raise FileNotFoundError(f"Schedule for {faculty} course {course} not found")

    groups = _filter_groups_for_course(
        faculty, course, get_groups_from_file(str(file_path))
    )
    total_lessons = 0

    db.query(ScheduleRecord).filter(
        ScheduleRecord.faculty == faculty,
        ScheduleRecord.course == course,
    ).delete()
    db.query(GroupsCache).filter(
        GroupsCache.faculty == faculty,
        GroupsCache.course == course,
    ).delete()

    db.add(GroupsCache(faculty=faculty, course=course, groups_json=groups))

    for group_name, subgroups in groups.items():
        for sub in subgroups or [""]:
            for week_type in WEEK_TYPES:
                schedule = parse_schedule_for_group(
                    str(file_path), group_name, sub or None, week_type=week_type
                )
                for day in schedule:
                    total_lessons += len(day.lessons)
                db.add(
                    ScheduleRecord(
                        faculty=faculty,
                        course=course,
                        group_name=group_name,
                        subgroup=sub or "",
                        week_type=week_type,
                        schedule_json=schedule_to_dict(schedule),
                    )
                )

    db.commit()
    return len(groups), total_lessons


def ensure_course_indexed(
    db: Session,
    course: int,
    force: bool = False,
    faculty: str = DEFAULT_FACULTY,
) -> None:
    """Cache Excel → JSON in SQLite. force=True rebuilds (needed after parser updates)."""
    faculty = normalize_faculty(faculty)
    if not force:
        exists = (
            db.query(ScheduleRecord.id)
            .filter(
                ScheduleRecord.faculty == faculty,
                ScheduleRecord.course == course,
            )
            .limit(1)
            .first()
        )
        if exists:
            return
    file_path = resolve_schedule_path(UPLOAD_DIR, faculty, course)
    if file_path is not None:
        index_course_file(db, course, faculty=faculty)


def ensure_all_indexed(db: Session, force: bool = False) -> None:
    for fac in list_faculties():
        for course in fac["courses"]:
            try:
                ensure_course_indexed(db, course, force=force, faculty=fac["id"])
            except Exception as exc:
                print(f"Index {fac['id']} course {course}: {exc}")
    try:
        week = get_current_week_type()
        if force:
            invalidate_index()
        existing = load_index(week) if not force else {"surnames": {}}
        # Rebuild only when missing / wrong week / forced — NOT on every API hit.
        if force or not (existing.get("surnames")):
            stats = rebuild_teacher_index(db, week)
            print(f"[teacher-index] {stats}")
    except Exception as exc:
        print(f"[teacher-index] rebuild failed: {exc}")


def _news_refresh_loop(interval_sec: int = 90) -> None:
    """Background: re-scrape dksta.ru often so new posts appear within ~1–2 minutes."""
    import threading
    import time

    from news_scraper import scrape_news

    def worker() -> None:
        # First loop after a short delay (startup already scrapes once)
        time.sleep(20)
        while True:
            try:
                n = scrape_news(20)
                top = n[0] if n else {}
                print(
                    f"[news-loop] refreshed {len(n)} items "
                    f"top={top.get('date')} {str(top.get('title', ''))[:60]}"
                )
            except Exception as exc:
                print(f"[news-loop] error: {exc}")
            time.sleep(interval_sec)

    t = threading.Thread(target=worker, name="news-refresh", daemon=True)
    t.start()


def _teachers_stale(max_age_sec: int = 24 * 3600) -> bool:
    if not TEACHERS_FILE.exists():
        return True
    try:
        age = time.time() - TEACHERS_FILE.stat().st_mtime
        return age > max_age_sec
    except OSError:
        return True


def refresh_teachers(force: bool = False) -> dict:
    """Scrape dksta.ru ППС → teachers.json. Safe to call from a background thread."""
    if not force and not _teachers_stale():
        return {"status": "fresh", "count": _teachers_count()}
    from scraper import scrape_all

    teachers = scrape_all()
    return {"status": "updated", "count": len(teachers or [])}


def _teachers_count() -> int:
    try:
        if not TEACHERS_FILE.exists():
            return 0
        with open(TEACHERS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        return len(data) if isinstance(data, list) else 0
    except Exception:
        return 0


def _teachers_refresh_loop(interval_sec: int = 12 * 3600) -> None:
    """Background: refresh teachers directory from the university site ~every 12h."""
    import threading
    import time

    def worker() -> None:
        time.sleep(45)  # let API finish startup / indexing first
        while True:
            try:
                result = refresh_teachers(force=False)
                print(f"[teachers-loop] {result}")
            except Exception as exc:
                print(f"[teachers-loop] error: {exc}")
            time.sleep(interval_sec)

    t = threading.Thread(target=worker, name="teachers-refresh", daemon=True)
    t.start()


@app.on_event("startup")
def startup():
    init_db()
    db = next(get_db())
    try:
        # Always re-parse on startup so fixes in parser.py are reflected in JSON cache
        ensure_all_indexed(db, force=True)
    finally:
        db.close()

    # Warm news cache immediately + keep refreshing in background (~every 90s)
    try:
        from news_scraper import scrape_news

        scrape_news(20)
    except Exception as exc:
        print(f"[news] startup scrape: {exc}")
    _news_refresh_loop(90)

    # Teachers directory: refresh in background if older than 24h, then every 12h
    import threading

    def _teachers_startup():
        try:
            print(f"[teachers] startup refresh → {refresh_teachers(force=False)}")
        except Exception as exc:
            print(f"[teachers] startup scrape: {exc}")

    threading.Thread(target=_teachers_startup, name="teachers-startup", daemon=True).start()
    _teachers_refresh_loop(12 * 3600)


@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}


def _normalize_teacher_position(pos: str) -> str:
    """Fix common glued words from source HTML (e.g. Заведующийинтеллектуальных)."""
    import re

    if not pos:
        return pos
    s = pos.replace("\xa0", " ").replace("\u200b", "")
    fixes = [
        ("Заведующийинтеллектуальных", "Заведующий кафедрой интеллектуальных"),
        ("заведующийинтеллектуальных", "заведующий кафедрой интеллектуальных"),
        ("системи комплексов", "систем и комплексов"),
        ("системикомплексов", "систем и комплексов"),
        ("наукдоцент", "наук, доцент"),
        ("ДоцентНачальник", "Доцент, начальник"),
        ("преподавательЗаместитель", "преподаватель, заместитель"),
        ("электроникиКафедра", "электроники. Кафедра"),
        ("кафедрыНачальник", "кафедры, начальник"),
    ]
    for a, b in fixes:
        s = s.replace(a, b)
    s = re.sub(r",([^\s])", r", \1", s)
    s = re.sub(r";([^\s])", r"; \1", s)
    s = re.sub(r"([а-яё])([А-ЯЁ])", r"\1, \2", s)
    s = re.sub(r"[ \t]+", " ", s)
    return s.strip(" ,;")


# Leadership not always present on кафедра ППС pages — keep them at the top of the list.
_LEADERSHIP_TEACHERS = [
    {
        "name": "Егоров Алексей Васильевич",
        "profile_url": "https://dksta.ru/",
        "photo_url": "",
        "position": "Ректор",
        "email": "",
        "subjects": [],
    },
]


def _ensure_leadership(teachers: list) -> list:
    """Inject rector/vice-rectors if missing from scraped ППС lists."""
    if not isinstance(teachers, list):
        return teachers
    existing = {
        (t.get("name") or "").strip().lower().replace("ё", "е")
        for t in teachers
        if isinstance(t, dict)
    }
    extra = []
    for lead in _LEADERSHIP_TEACHERS:
        key = lead["name"].strip().lower().replace("ё", "е")
        if key not in existing:
            extra.append(dict(lead))
    return extra + teachers if extra else teachers


@app.get("/api/teachers")
def get_teachers():
    if not TEACHERS_FILE.exists():
        return {"teachers": _ensure_leadership([])}
    with open(TEACHERS_FILE, "r", encoding="utf-8") as f:
        teachers = json.load(f)
    if isinstance(teachers, list):
        for t in teachers:
            if isinstance(t, dict) and "position" in t:
                t["position"] = _normalize_teacher_position(str(t.get("position") or ""))
        teachers = _ensure_leadership(teachers)
    meta = {}
    try:
        st = TEACHERS_FILE.stat()
        meta = {
            "updatedAt": datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat(),
            "count": len(teachers) if isinstance(teachers, list) else 0,
        }
    except OSError:
        pass
    return {"teachers": teachers, **meta}


@app.post("/admin/refresh-teachers")
def admin_refresh_teachers(
    password: str = Form(...),
    force: str = Form("true"),
):
    """Manual scrape of university ППС pages → teachers.json."""
    verify_admin_password(password)
    do_force = str(force).lower() in ("1", "true", "yes", "on")
    try:
        return refresh_teachers(force=do_force)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.get("/api/news")
def get_news(
    limit: int = Query(15, ge=1, le=50),
    force: bool = Query(False, description="Ask background scrape; response still from cache"),
):
    """
    News from dksta.ru (merged cache).
    Always returns cache immediately — never blocks on live scrape.
    force=true schedules a background refresh; loop also refreshes ~every 90s.
    """
    from news_scraper import get_news_fast, load_cached_news

    try:
        news = get_news_fast(limit, force=force)
    except Exception as exc:
        print(f"[news] api scrape failed: {exc}")
        news = load_cached_news()[:limit]
    from news_scraper import load_meta

    meta = load_meta()
    scraped_ts = float(meta.get("last_scrape_ts") or 0)
    return {
        "news": news,
        "count": len(news),
        # Clients use this for «Обновлено …» — seconds since epoch.
        "updatedAt": scraped_ts if scraped_ts > 0 else None,
        "lastOk": bool(meta.get("last_ok", True)),
        "lastError": meta.get("last_error") or "",
    }


@app.get("/api/news-updates")
def news_updates():
    """
    Lightweight fingerprint of the news feed for client polling.
    Apps poll this (e.g. every 10–15 min) and show a local notification
    when fingerprint / version changes (new post on dksta.ru).
    """
    from news_scraper import news_updates_payload

    return news_updates_payload()


@app.get("/api/faculties")
def get_faculties(db: Session = Depends(get_db)):
    """List faculties and which courses have schedule files / cache."""
    result = []
    for fac in list_faculties():
        fid = fac["id"]
        courses = []
        for course in fac["courses"]:
            path = resolve_schedule_path(UPLOAD_DIR, fid, course)
            cached = (
                db.query(ScheduleRecord.id)
                .filter(
                    ScheduleRecord.faculty == fid,
                    ScheduleRecord.course == course,
                )
                .limit(1)
                .first()
                is not None
            )
            courses.append(
                {
                    "course": course,
                    "available": path is not None or cached,
                }
            )
        result.append(
            {
                "id": fid,
                "short": fac["short"],
                "name": fac["name"],
                "courses": courses,
            }
        )
    return {"faculties": result, "defaultFaculty": DEFAULT_FACULTY}


@app.get("/api/courses")
def get_courses(
    faculty: str = Query(DEFAULT_FACULTY),
    db: Session = Depends(get_db),
):
    try:
        fid = normalize_faculty(faculty)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    courses = []
    for i in courses_for(fid):
        path = resolve_schedule_path(UPLOAD_DIR, fid, i)
        cached = (
            db.query(ScheduleRecord.id)
            .filter(
                ScheduleRecord.faculty == fid,
                ScheduleRecord.course == i,
            )
            .limit(1)
            .first()
            is not None
        )
        courses.append(
            {
                "course": i,
                "faculty": fid,
                "available": path is not None or cached,
            }
        )
    return courses


@app.get("/api/week-type")
def week_type():
    return {"weekType": get_current_week_type()}


def _course_file_version(faculty: str, course: int) -> Optional[dict]:
    """
    Stable version for a course schedule file.
    Changes only when the Excel on disk is replaced (mtime/size),
    not when the server merely re-indexes JSON cache.
    """
    path = resolve_schedule_path(UPLOAD_DIR, faculty, course)
    if path is None:
        return None
    try:
        st = path.stat()
    except OSError:
        return None
    version = f"{int(st.st_mtime)}-{st.st_size}"
    updated_at = datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat()
    return {
        "faculty": faculty,
        "course": course,
        "version": version,
        "updatedAt": updated_at,
        "available": True,
    }


@app.get("/api/schedule-updates")
def schedule_updates(faculty: Optional[str] = Query(None)):
    """
    Lightweight endpoint for the app to detect new Excel uploads.
    Clients poll this (e.g. every 15 min) and show a push-style notification
    when their course version changes.
    """
    faculties = []
    if faculty:
        try:
            fac_ids = [normalize_faculty(faculty)]
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
    else:
        fac_ids = [f["id"] for f in list_faculties()]

    courses = []
    for fid in fac_ids:
        fac_courses = []
        for i in courses_for(fid):
            info = _course_file_version(fid, i)
            if info is None:
                item = {
                    "faculty": fid,
                    "course": i,
                    "version": "",
                    "updatedAt": None,
                    "available": False,
                }
            else:
                item = info
            fac_courses.append(item)
            courses.append(item)
        faculties.append(
            {
                "id": fid,
                "short": faculty_meta(fid)["short"],
                "courses": fac_courses,
            }
        )
    fingerprint = "|".join(
        f"{c['faculty']}:{c['course']}:{c['version']}"
        for c in courses
        if c.get("version")
    )
    return {
        "faculties": faculties,
        "courses": courses,  # flat list for older clients
        "fingerprint": fingerprint,
    }


@app.get("/api/groups-debug")
def get_groups_debug(course: int = Query(..., ge=1, le=4)):
    """Temporary diagnostics: how the parser sees the Excel header on the server."""
    from parser import (
        open_workbook,
        close_workbook,
        parse_groups_from_header,
        extract_subgroup,
        extract_group_name,
        get_groups_from_file,
    )

    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
    if not file_path.exists():
        raise HTTPException(status_code=404, detail="file missing")

    sheet, wb = open_workbook(str(file_path))
    try:
        row2 = {}
        row3 = {}
        for c in range(min(40, sheet.max_column)):
            t2 = sheet.get_cell_text(2, c)
            if t2:
                row2[str(c)] = t2
            r3 = sheet.get_cell_text(3, c)
            m3 = sheet.get_text_with_merged(3, c)
            if r3 or m3:
                row3[str(c)] = {
                    "raw": r3,
                    "merged": m3,
                    "subgroup": extract_subgroup(m3 or r3),
                }
        merges = []
        for mg in sheet.merged_ranges:
            if mg["min_row"] <= 3 and mg["max_row"] >= 2 and mg["min_col"] <= 35:
                merges.append(
                    {
                        "rows": [mg["min_row"], mg["max_row"]],
                        "cols": [mg["min_col"], mg["max_col"]],
                        "val": sheet.get_cell_text(mg["min_row"], mg["min_col"]),
                    }
                )
        parsed = {
            name: [(s.name, s.column) for s in info.subgroups]
            for name, info in parse_groups_from_header(sheet).items()
        }
        # sample: does col 15 have own lessons vs 13?
        diffs = []
        for row in range(4, 40):
            a = sheet.get_cell_text(row, 13)
            b = sheet.get_cell_text(row, 15)
            if a or b:
                diffs.append(
                    {
                        "row": row,
                        "c13_raw": (a or "")[:50],
                        "c15_raw": (b or "")[:50],
                    }
                )
                if len(diffs) >= 12:
                    break
        return {
            "file": str(file_path),
            "size": file_path.stat().st_size,
            "is_xls": sheet.is_xls,
            "groups_api": get_groups_from_file(str(file_path)),
            "parsed_columns": parsed,
            "row2": row2,
            "row3": row3,
            "header_merges": merges,
            "sample_c13_c15": diffs,
        }
    finally:
        close_workbook(sheet, wb)


@app.get("/api/groups")
def get_groups(
    course: int = Query(..., ge=1, le=5),
    faculty: str = Query(DEFAULT_FACULTY),
    db: Session = Depends(get_db),
):
    """
    Fast path: return GroupsCache from SQLite (filled on Excel upload / index).
    Re-parse Excel only on cache miss — parsing on every request was ~5–8s and
    made faculty/course switching unusable on VPN.
    """
    try:
        fid = normalize_faculty(faculty)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if course not in courses_for(fid):
        raise HTTPException(status_code=400, detail=f"No course {course} for {fid}")

    try:
        cached = (
            db.query(GroupsCache)
            .filter(GroupsCache.faculty == fid, GroupsCache.course == course)
            .first()
        )
        if cached and cached.groups_json:
            return cached.groups_json
    except Exception as exc:
        print(f"[groups] cache read error {fid}/{course}: {exc}")
        try:
            db.rollback()
        except Exception:
            pass

    file_path = resolve_schedule_path(UPLOAD_DIR, fid, course)
    if file_path is None:
        raise HTTPException(
            status_code=404, detail=f"Schedule for {fid} course {course} not found"
        )

    try:
        groups = _filter_groups_for_course(
            fid, course, get_groups_from_file(str(file_path))
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Parse error: {exc}") from exc

    try:
        existing = (
            db.query(GroupsCache)
            .filter(GroupsCache.faculty == fid, GroupsCache.course == course)
            .first()
        )
        if existing:
            existing.groups_json = groups
        else:
            db.add(GroupsCache(faculty=fid, course=course, groups_json=groups))
        db.commit()
    except Exception as exc:
        print(f"[groups] cache write failed: {exc}")
        try:
            db.rollback()
        except Exception:
            pass

    return groups


def _resolve_week_type(week: Optional[str]) -> str:
    """Accept Числитель/Знаменатель or numerator/denominator aliases; default = current."""
    raw = (week or "").strip()
    if not raw:
        return get_current_week_type()
    low = raw.lower().replace("ё", "е")
    if low in ("числитель", "numerator", "num", "current", "эта", "this"):
        # "current" means the academic current week, not "whatever client sent empty"
        if low in ("current", "эта", "this"):
            return get_current_week_type()
        return "Числитель"
    if low in ("знаменатель", "denominator", "den", "next", "следующая"):
        if low in ("next", "следующая"):
            cur = get_current_week_type()
            return "Знаменатель" if cur == "Числитель" else "Числитель"
        return "Знаменатель"
    if raw in WEEK_TYPES:
        return raw
    return get_current_week_type()


@app.get("/api/schedule")
def get_schedule(
    course: int = Query(..., ge=1, le=5),
    group: str = Query(...),
    subgroup: Optional[str] = Query(None),
    faculty: str = Query(DEFAULT_FACULTY),
    week: Optional[str] = Query(
        None,
        description="Числитель | Знаменатель | next (другая относительно текущей)",
    ),
    db: Session = Depends(get_db),
):
    try:
        fid = normalize_faculty(faculty)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if course not in courses_for(fid):
        raise HTTPException(status_code=400, detail=f"No course {course} for {fid}")

    week = _resolve_week_type(week)
    subgroup_key = subgroup or ""

    record = None
    try:
        ensure_course_indexed(db, course, faculty=fid)
        record = (
            db.query(ScheduleRecord)
            .filter(
                ScheduleRecord.faculty == fid,
                ScheduleRecord.course == course,
                ScheduleRecord.group_name == group,
                ScheduleRecord.subgroup == subgroup_key,
                ScheduleRecord.week_type == week,
            )
            .first()
        )

        # Fallback: try empty subgroup or first available for group
        if record is None and subgroup_key:
            record = (
                db.query(ScheduleRecord)
                .filter(
                    ScheduleRecord.faculty == fid,
                    ScheduleRecord.course == course,
                    ScheduleRecord.group_name == group,
                    ScheduleRecord.week_type == week,
                )
                .first()
            )
    except Exception as exc:
        print(f"[schedule] db error: {exc}")
        try:
            db.rollback()
        except Exception:
            pass

    if record is not None:
        return {
            "faculty": fid,
            "course": course,
            "group": group,
            "subgroup": subgroup,
            "weekType": week,
            "fromCache": True,
            "schedule": schedule_days_from_json(record.schedule_json),
        }

    # Last resort: live parse from Excel
    file_path = resolve_schedule_path(UPLOAD_DIR, fid, course)
    if file_path is None:
        raise HTTPException(
            status_code=404, detail=f"Schedule for {fid} course {course} not found"
        )

    schedule = parse_schedule_for_group(str(file_path), group, subgroup, week_type=week)
    return {
        "faculty": fid,
        "course": course,
        "group": group,
        "subgroup": subgroup,
        "weekType": week,
        "fromCache": False,
        "schedule": schedule_to_dict(schedule),
    }


@app.get("/api/schedule/by-teacher")
def schedule_by_teacher(
    q: str = Query(..., min_length=2, description="Фамилия или ФИО преподавателя"),
    day: str = Query("today", description="today | week | Понедельник…"),
    faculty: Optional[str] = Query(None),
    week: Optional[str] = Query(None, description="Числитель | Знаменатель | next"),
    db: Session = Depends(get_db),
):
    """
    Fast cross-group lookup via inverted teacher index (rebuilt on schedule upload).
    """
    week = _resolve_week_type(week)
    query = q.strip()

    # Hot path: never re-parse Excel here. Rebuild inverted index only if missing
    # for the requested week (e.g. user asked for Знаменатель while index is Числитель).
    if not (load_index(week).get("surnames")):
        try:
            ensure_all_indexed(db, force=False)
            if not (load_index(week).get("surnames")):
                rebuild_teacher_index(db, week)
        except Exception as exc:
            print(f"[teacher-index] lazy rebuild {week}: {exc}")

    day_key = (day or "today").strip()
    if day_key.lower() in ("today", "сегодня"):
        wanted_days = {get_today_name()}
    elif day_key.lower() in ("week", "неделя", "all", "*"):
        wanted_days = None
    else:
        wanted_days = {day_key}

    lessons_out = lookup_lessons(query, week, wanted_days)
    if faculty:
        try:
            fid = normalize_faculty(faculty)
            lessons_out = [x for x in lessons_out if x.get("faculty") == fid]
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "query": query,
        "weekType": week,
        "day": day_key,
        "todayName": get_today_name(),
        "count": len(lessons_out),
        "lessons": lessons_out,
    }


@app.post("/admin/validate")
async def validate_schedule(
    file: UploadFile = File(...),
    password: str = Form(...),
    course: Optional[int] = Form(None),
):
    """Check Excel for parse issues without publishing to production cache."""
    verify_admin_password(password)
    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(status_code=400, detail="Нужен файл .xlsx или .xls")

    suffix = Path(file.filename).suffix or ".xlsx"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        shutil.copyfileobj(file.file, tmp)
        tmp_path = tmp.name
    try:
        result = validate_schedule_file(tmp_path)
        result["filename"] = file.filename
        if course is not None:
            result["course"] = course
        return result
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass


@app.post("/admin/upload")
async def upload_schedule(
    course: int = Form(..., ge=1, le=5),
    file: UploadFile = File(...),
    password: str = Form(...),
    db: Session = Depends(get_db),
    skip_validate: str = Form("false"),
    faculty: str = Form(DEFAULT_FACULTY),
):
    verify_admin_password(password)

    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(status_code=400, detail="Only Excel files are accepted")

    try:
        fid = normalize_faculty(faculty)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    do_skip = str(skip_validate).lower() in ("1", "true", "yes", "on")

    # Save to temp first → validate → then publish
    suffix = Path(file.filename).suffix or ".xlsx"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        shutil.copyfileobj(file.file, tmp)
        tmp_path = tmp.name

    validation = None
    try:
        if not do_skip:
            validation = validate_schedule_file(tmp_path)
            if validation.get("errors"):
                raise HTTPException(
                    status_code=400,
                    detail={
                        "message": "Файл не прошёл проверку",
                        "validation": validation,
                    },
                )

        dest_name = target_filename(fid, course, suffix=suffix.lower())
        file_path = UPLOAD_DIR / dest_name
        shutil.copy2(tmp_path, file_path)
        # Force mtime = now so /api/schedule-updates version always changes
        # even if the same Excel bytes are re-uploaded (copy2 keeps source mtime).
        os.utime(file_path, None)

        # МТФ 4–5: one file feeds both courses
        courses_to_index = [4, 5] if (fid == "mtf" and course in (4, 5)) else [course]
        total_groups = 0
        total_lessons = 0
        index_errors: list[str] = []
        for c in courses_to_index:
            if c not in courses_for(fid):
                continue
            try:
                g, les = index_course_file(db, c, file.filename, faculty=fid)
                total_groups += g
                total_lessons += les
            except Exception as idx_exc:
                index_errors.append(f"course {c}: {idx_exc}")
                print(f"[upload] index {fid}/{c}: {idx_exc}")

        # File is on disk even if indexing failed (e.g. magistracy grid)
        status = "success" if not index_errors else ("partial" if total_groups else "saved")
        log = UploadLog(
            filename=file.filename,
            faculty=fid,
            course=course,
            groups_count=total_groups,
            lessons_count=total_lessons,
            status=status,
            error_message="; ".join(index_errors) if index_errors else None,
        )
        db.add(log)
        db.commit()
        try:
            invalidate_index()
            rebuild_teacher_index(db, get_current_week_type())
        except Exception as exc:
            print(f"[teacher-index] after upload: {exc}")
        return {
            "status": status,
            "filename": file.filename,
            "savedAs": dest_name,
            "faculty": fid,
            "course": course,
            "indexedCourses": courses_to_index,
            "groups_count": total_groups,
            "lessons_count": total_lessons,
            "validation": validation,
            "indexErrors": index_errors,
            "message": (
                None
                if not index_errors
                else "Файл сохранён на сервере, но индексация групп частично/не удалась (нужен другой парсер сетки)."
            ),
        }
    except HTTPException:
        raise
    except Exception as e:
        log = UploadLog(
            filename=file.filename,
            faculty=fid,
            course=course,
            status="error",
            error_message=str(e),
        )
        db.add(log)
        db.commit()
        raise HTTPException(status_code=500, detail=f"Parse error: {str(e)}") from e
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass


def _iso_utc(dt: Optional[datetime]) -> Optional[str]:
    """Serialize naive DB datetimes as UTC so browsers show correct local time."""
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.isoformat()


@app.get("/admin/status")
def admin_status(
    x_admin_password: Optional[str] = Header(None, alias="X-Admin-Password"),
    password: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    verify_admin_password(x_admin_password or password or "")

    logs = db.query(UploadLog).order_by(UploadLog.uploaded_at.desc()).limit(20).all()
    return {
        "uploads": [
            {
                "filename": log.filename,
                "faculty": getattr(log, "faculty", None) or "fae",
                "course": log.course,
                "groups_count": log.groups_count,
                "lessons_count": log.lessons_count,
                # Always mark as UTC (stored with datetime.utcnow)
                "uploaded_at": _iso_utc(log.uploaded_at),
                "status": log.status,
                "error": log.error_message,
            }
            for log in logs
        ]
    }


def _backup_file_entries() -> list[dict]:
    """List files that can be downloaded for backup."""
    entries: list[dict] = []
    for course in range(1, 5):
        path = UPLOAD_DIR / f"schedule{course}.xlsx"
        if path.is_file():
            st = path.stat()
            entries.append({
                "id": f"schedule{course}",
                "name": path.name,
                "label": f"Excel · {course} курс",
                "size": st.st_size,
                "mtime": datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat(),
            })
    db_path = Path("schedule.db")
    if db_path.is_file():
        st = db_path.stat()
        entries.append({
            "id": "schedule.db",
            "name": "schedule.db",
            "label": "База SQLite (разобранное расписание)",
            "size": st.st_size,
            "mtime": datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat(),
        })
    if TEACHERS_FILE.is_file():
        st = TEACHERS_FILE.stat()
        entries.append({
            "id": "teachers.json",
            "name": "teachers.json",
            "label": "Преподаватели (JSON)",
            "size": st.st_size,
            "mtime": datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat(),
        })
    return entries


def _resolve_backup_path(file_id: str) -> Path:
    allowed = {
        "schedule1": UPLOAD_DIR / "schedule1.xlsx",
        "schedule2": UPLOAD_DIR / "schedule2.xlsx",
        "schedule3": UPLOAD_DIR / "schedule3.xlsx",
        "schedule4": UPLOAD_DIR / "schedule4.xlsx",
        "schedule.db": Path("schedule.db"),
        "teachers.json": TEACHERS_FILE,
    }
    path = allowed.get(file_id)
    if path is None or not path.is_file():
        raise HTTPException(status_code=404, detail="Файл не найден")
    return path


@app.get("/admin/backup/list")
def admin_backup_list(
    x_admin_password: Optional[str] = Header(None, alias="X-Admin-Password"),
    password: Optional[str] = Query(None),
):
    """List Excel + DB files available for download."""
    verify_admin_password(x_admin_password or password or "")
    return {"files": _backup_file_entries()}


@app.get("/admin/backup/download/{file_id}")
def admin_backup_download(
    file_id: str,
    x_admin_password: Optional[str] = Header(None, alias="X-Admin-Password"),
    password: Optional[str] = Query(None),
):
    """Download one backup file (Excel / DB / teachers)."""
    verify_admin_password(x_admin_password or password or "")
    path = _resolve_backup_path(file_id)
    return FileResponse(
        path=str(path),
        filename=path.name,
        media_type="application/octet-stream",
    )


@app.get("/admin/backup/zip")
def admin_backup_zip(
    x_admin_password: Optional[str] = Header(None, alias="X-Admin-Password"),
    password: Optional[str] = Query(None),
):
    """ZIP of all Excel schedules + schedule.db + teachers.json (if present)."""
    verify_admin_password(x_admin_password or password or "")
    entries = _backup_file_entries()
    if not entries:
        raise HTTPException(status_code=404, detail="Нет файлов для бэкапа")

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for e in entries:
            path = _resolve_backup_path(e["id"])
            zf.write(path, arcname=path.name)
    buf.seek(0)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M")
    return StreamingResponse(
        buf,
        media_type="application/zip",
        headers={
            "Content-Disposition": f'attachment; filename="studentapp-backup-{stamp}.zip"',
        },
    )


@app.get("/admin", response_class=HTMLResponse)
async def admin_panel():
    """Admin UI for schedule staff — no SSH required."""
    html = r"""
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Загрузка расписания — StudentApp</title>
<link rel="icon" type="image/png" href="/static/favicon.png?v=2">
<link rel="shortcut icon" href="/favicon.ico?v=2">
<link rel="apple-touch-icon" href="/static/favicon.png?v=2">
<style>
  :root { --blue:#1a336c; --bg:#eef2f7; --ok:#1b7a3d; --err:#b42318; --warn:#b54708; }
  * { box-sizing: border-box; }
  body { font-family: system-ui, -apple-system, sans-serif; max-width: 920px; margin: 0 auto; padding: 24px 16px 48px; background: var(--bg); color: #1a1a1a; }
  h1 { color: var(--blue); margin: 0 0 8px; font-size: 1.6rem; }
  .sub { color: #5f6b7a; margin-bottom: 20px; line-height: 1.45; }
  .card { background: #fff; padding: 20px; border-radius: 14px; box-shadow: 0 2px 10px rgba(0,0,0,.06); margin: 16px 0; }
  label { display: block; margin: 10px 0 6px; font-weight: 600; font-size: 14px; }
  input[type=password], input[type=file] { width: 100%; padding: 10px 12px; border: 1px solid #c5ced9; border-radius: 10px; font-size: 15px; }
  .row { display: grid; grid-template-columns: 1fr; gap: 12px; }
  @media (min-width: 700px) { .row { grid-template-columns: 1fr 1fr; } }
  .course-box { border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px; background: #fafbfc; }
  .course-box h3 { margin: 0 0 10px; color: var(--blue); font-size: 1rem; }
  .btns { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
  button { background: var(--blue); color: #fff; border: none; border-radius: 10px; padding: 11px 16px; font-weight: 600; cursor: pointer; font-size: 14px; }
  button.secondary { background: #fff; color: var(--blue); border: 1.5px solid var(--blue); }
  button:disabled { opacity: .55; cursor: not-allowed; }
  button.danger { background: var(--err); }
  .ok { color: var(--ok); } .err { color: var(--err); } .warn { color: var(--warn); }
  .report { font-size: 13px; margin-top: 10px; line-height: 1.4; max-height: 220px; overflow: auto; white-space: pre-wrap; background: #f4f6f8; padding: 10px; border-radius: 8px; }
  table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
  th, td { border: 1px solid #e2e6ea; padding: 8px; text-align: left; }
  th { background: var(--blue); color: #fff; }
  .hint { font-size: 13px; color: #5f6b7a; margin-top: 8px; }
  .tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
  .tab { background: #e8eef8; color: var(--blue); border: 1.5px solid #c5d0e6; border-radius: 999px; padding: 8px 14px; font-weight: 700; cursor: pointer; font-size: 14px; }
  .tab.active { background: var(--blue); color: #fff; border-color: var(--blue); }
  .slot-hint { font-size: 12px; color: #5f6b7a; margin: 0 0 8px; }
</style>
</head>
<body>
  <h1>Расписание → приложение</h1>
  <p class="sub">Для сотрудника, который готовит Excel. Пароль один раз, файлы по факультетам и курсам — проверить, потом опубликовать. SSH и консоль не нужны.</p>

  <div class="card">
    <label>Пароль администратора</label>
    <input type="password" id="password" autocomplete="current-password" placeholder="Пароль из .env сервера">
    <p class="hint">Пароль не попадает в адресную строку. Сохраните его только у УМУ / ответственного.</p>
  </div>

  <div class="card">
    <h2 style="margin-top:0;color:var(--blue);font-size:1.15rem;">Файлы по факультетам</h2>
    <div class="tabs" id="facultyTabs"></div>
    <p class="hint" id="facultyHint">1) Выберите факультет → 2) Excel → 3) «Проверить» → 4) «Опубликовать».</p>
    <div class="row" id="courses"></div>
    <div class="btns" style="margin-top:16px;">
      <button type="button" id="btnValidateAll" class="secondary">Проверить все выбранные</button>
      <button type="button" id="btnPublishAll">Опубликовать все проверенные</button>
    </div>
    <div id="globalLog" class="report" style="display:none;"></div>
  </div>

  <div class="card">
    <h2 style="margin-top:0;color:var(--blue);font-size:1.15rem;">Бэкап</h2>
    <p class="hint">Скачайте Excel и базу, чтобы не потерять расписание (на компьютер / флешку / облако).</p>
    <div class="btns">
      <button type="button" id="btnBackupZip">Скачать всё (ZIP)</button>
      <button type="button" id="btnBackupList" class="secondary">Обновить список файлов</button>
    </div>
    <div id="backupList" class="report" style="display:none;margin-top:12px;"></div>
  </div>

  <div class="card">
    <h2 style="margin-top:0;color:var(--blue);font-size:1.15rem;">История</h2>
    <button type="button" id="refreshBtn" class="secondary">Обновить историю</button>
    <table>
      <thead><tr><th>Дата</th><th>Файл</th><th>Фак.</th><th>Курс</th><th>Групп</th><th>Пар</th><th>Статус</th></tr></thead>
      <tbody id="historyBody"></tbody>
    </table>
  </div>

<script>
const FACULTIES = [
  { id: 'fae', short: 'АиЭ', name: 'Автоматика и электроника',
    slots: [
      { course: 1, label: '1 курс' },
      { course: 2, label: '2 курс' },
      { course: 3, label: '3 курс' },
      { course: 4, label: '4 курс' },
    ]},
  { id: 'mtf', short: 'МТФ', name: 'Машиностроительный технологический',
    slots: [
      { course: 1, label: '1 курс' },
      { course: 2, label: '2 курс' },
      { course: 3, label: '3 курс' },
      { course: 4, label: '4–5 курс', hint: 'Один Excel на 4 и 5 курс' },
    ]},
  { id: 'masters', short: 'Маг.', name: 'Магистратура (очное)',
    slots: [
      { course: 2, label: '2 курс (очное)', hint: 'Файл магистров. Если проверка ругается на сетку — всё равно можно опубликовать.' },
    ]},
];

let currentFaculty = 'fae';
const state = {}; // key faculty:course -> { file, validation }

function slotKey(c) { return currentFaculty + ':' + c; }
function pw() { return document.getElementById('password').value || ''; }

function ensurePw() {
  if (!pw()) { alert('Сначала введите пароль администратора'); return false; }
  return true;
}

function currentSlots() {
  return (FACULTIES.find(f => f.id === currentFaculty) || FACULTIES[0]).slots;
}

function renderFacultyTabs() {
  const root = document.getElementById('facultyTabs');
  root.innerHTML = '';
  FACULTIES.forEach(f => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'tab' + (f.id === currentFaculty ? ' active' : '');
    b.textContent = f.short;
    b.title = f.name;
    b.onclick = () => {
      currentFaculty = f.id;
      renderFacultyTabs();
      renderCourses();
      document.getElementById('facultyHint').textContent =
        f.name + ' — выберите Excel, проверьте и опубликуйте.';
    };
    root.appendChild(b);
  });
}

function renderCourses() {
  const root = document.getElementById('courses');
  root.innerHTML = '';
  currentSlots().forEach(slot => {
    const c = slot.course;
    const key = slotKey(c);
    if (!state[key]) state[key] = { validation: null };
    const box = document.createElement('div');
    box.className = 'course-box';
    box.innerHTML = `
      <h3>${slot.label}</h3>
      ${slot.hint ? `<p class="slot-hint">${slot.hint}</p>` : ''}
      <input type="file" id="file${key}" accept=".xlsx,.xls">
      <div class="btns">
        <button type="button" class="secondary" data-act="validate" data-c="${c}">Проверить</button>
        <button type="button" data-act="publish" data-c="${c}">Опубликовать</button>
      </div>
      <div class="report" id="rep${key}" style="display:none;"></div>`;
    root.appendChild(box);
  });
  root.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', () => {
      const c = +btn.dataset.c;
      if (btn.dataset.act === 'validate') validateOne(c);
      else publishOne(c);
    });
  });
}

function showReport(c, html, kind) {
  const el = document.getElementById('rep' + slotKey(c));
  if (!el) return;
  el.style.display = 'block';
  el.className = 'report ' + (kind || '');
  el.innerHTML = html;
}

function formatValidation(v) {
  if (!v) return '';
  const st = v.stats || {};
  let h = '';
  if (v.ok) h += `<div class="ok"><b>Проверка пройдена</b> · названий групп: ${st.groups || 0}, столбцов (треков) расписания: ${st.subgroups || 0}</div>`;
  else h += `<div class="err"><b>Есть ошибки</b></div>`;
  if (v.validator_version) h += `<div class="hint">Версия проверки: <code>${v.validator_version}</code></div>`;
  if (st.breakdown && st.breakdown.length) {
    h += `<div class="hint" style="margin-top:8px"><b>Как посчитано:</b><br>`;
    st.breakdown.forEach(b => {
      h += `• <b>${b.group}</b> — ${b.count} столбца: ${(b.subgroups || []).join(', ')}<br>`;
    });
    h += `«Подгруппа» здесь = отдельный столбец в Excel.</div>`;
  }
  if (st.debug_N49) {
    const d = st.debug_N49;
    h += `<div class="hint" style="margin-top:8px;border:1px solid #ccd;padding:8px;border-radius:8px">`;
    h += `<b>Диагностика N49</b> (чтобы понять merge)<br>`;
    h += `N49: ${d.N49_raw ? d.N49_raw : '(пусто)'}<br>`;
    h += `P49: ${d.P49_raw ? d.P49_raw : '(пусто)'}<br>`;
    h += `N50: ${d.N50_raw ? d.N50_raw : '(пусто)'}<br>`;
    h += `P50: ${d.P50_raw ? d.P50_raw : '(пусто)'}<br>`;
    h += `has_merge: <b>${d.has_merge}</b><br>`;
    h += `<i>${d.note || ''}</i></div>`;
  }
  (v.errors || []).forEach(e => { h += `<div class="err">✕ ${e}</div>`; });
  (v.warnings || []).forEach(w => { h += `<div class="warn">⚠ ${w}</div>`; });
  if (!(v.errors||[]).length && !(v.warnings||[]).length) h += `<div class="ok">Замечаний нет</div>`;
  return h;
}

async function readJson(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch (e) {
    const short = (text || '').slice(0, 180).replace(/</g, '&lt;');
    throw new Error('Сервер вернул не JSON (HTTP ' + res.status + '): ' + short);
  }
}

async function validateOne(c) {
  if (!ensurePw()) return;
  const key = slotKey(c);
  const input = document.getElementById('file' + key);
  const f = input && input.files[0];
  if (!f) { alert('Выберите файл'); return; }
  showReport(c, 'Проверка…', '');
  const fd = new FormData();
  fd.append('password', pw());
  fd.append('file', f);
  fd.append('course', c);
  fd.append('faculty', currentFaculty);
  try {
    const res = await fetch('/admin/validate', { method: 'POST', body: fd });
    const data = await readJson(res);
    if (!res.ok) {
      showReport(c, '<span class="err">' + (data.detail || res.status) + '</span>', 'err');
      state[key].validation = null;
      return;
    }
    state[key].validation = data;
    state[key].file = f;
    showReport(c, formatValidation(data), data.ok ? 'ok' : 'err');
  } catch (e) {
    showReport(c, '<span class="err">Ошибка: ' + e.message + '</span>', 'err');
  }
}

async function publishOne(c, force) {
  if (!ensurePw()) return;
  const key = slotKey(c);
  const input = document.getElementById('file' + key);
  const f = input && input.files[0];
  if (!f) { alert('Выберите файл'); return; }
  if (!state[key].validation && !force) {
    const go = confirm('Файл ещё не проверен. Сначала проверить, потом опубликовать?\\nОК = только проверить, Отмена = отмена.');
    if (go) { await validateOne(c); return; }
    return;
  }
  if (state[key].validation && (state[key].validation.warnings||[]).length && !force) {
    if (!confirm('Есть предупреждения. Всё равно опубликовать?')) return;
  }
  showReport(c, 'Публикация…', '');
  const fd = new FormData();
  fd.append('password', pw());
  fd.append('file', f);
  fd.append('course', c);
  fd.append('faculty', currentFaculty);
  // Магистратура / нестандартная сетка — разрешаем публикацию без жёсткой проверки
  if (force || currentFaculty === 'masters') fd.append('skip_validate', 'true');
  try {
    const res = await fetch('/admin/upload', { method: 'POST', body: fd });
    const data = await readJson(res);
    if (res.ok) {
      let h = `<div class="ok"><b>Опубликовано</b> · ${currentFaculty} · групп: ${data.groups_count}, пар: ${data.lessons_count}</div>`;
      if (data.savedAs) h += `<div class="hint">Файл на сервере: <code>${data.savedAs}</code></div>`;
      if (data.indexedCourses) h += `<div class="hint">Проиндексированы курсы: ${(data.indexedCourses||[]).join(', ')}</div>`;
      if (data.validation) h += formatValidation(data.validation);
      showReport(c, h, 'ok');
      loadHistory();
    } else {
      const d = data.detail;
      let msg = typeof d === 'object' ? JSON.stringify(d) : (d || res.status);
      if (d && d.validation) msg = formatValidation(d.validation);
      showReport(c, '<span class="err">Ошибка публикации</span><br>' + msg, 'err');
    }
  } catch (e) {
    showReport(c, '<span class="err">Ошибка: ' + e.message + '</span>', 'err');
  }
}

document.getElementById('btnValidateAll').onclick = async () => {
  for (const slot of currentSlots()) {
    const key = slotKey(slot.course);
    const input = document.getElementById('file' + key);
    if (input && input.files[0]) await validateOne(slot.course);
  }
};
document.getElementById('btnPublishAll').onclick = async () => {
  if (!ensurePw()) return;
  if (!confirm('Опубликовать все выбранные файлы текущего факультета?')) return;
  for (const slot of currentSlots()) {
    const key = slotKey(slot.course);
    const input = document.getElementById('file' + key);
    if (input && input.files[0]) await publishOne(slot.course, true);
  }
};

async function loadHistory() {
  if (!pw()) return;
  const res = await fetch('/admin/status', { headers: { 'X-Admin-Password': pw() } });
  if (!res.ok) return;
  const data = await res.json();
  const tbody = document.getElementById('historyBody');
  tbody.innerHTML = '';
  (data.uploads || []).forEach(u => {
    const tr = document.createElement('tr');
    const when = u.uploaded_at
      ? new Date(u.uploaded_at).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
      : '';
    tr.innerHTML = `<td>${when}</td>
      <td>${u.filename || ''}</td><td>${u.faculty || 'fae'}</td><td>${u.course}</td><td>${u.groups_count}</td>
      <td>${u.lessons_count}</td>
      <td class="${u.status === 'success' ? 'ok' : 'err'}">${u.status}</td>`;
    tbody.appendChild(tr);
  });
}
document.getElementById('refreshBtn').onclick = loadHistory;

function fmtSize(n) {
  if (n < 1024) return n + ' B';
  if (n < 1024*1024) return (n/1024).toFixed(1) + ' KB';
  return (n/1024/1024).toFixed(1) + ' MB';
}

async function loadBackupList() {
  if (!ensurePw()) return;
  const box = document.getElementById('backupList');
  box.style.display = 'block';
  box.innerHTML = 'Загрузка…';
  try {
    const res = await fetch('/admin/backup/list', { headers: { 'X-Admin-Password': pw() } });
    const data = await res.json();
    if (!res.ok) {
      box.innerHTML = '<span class="err">' + (data.detail || res.status) + '</span>';
      return;
    }
    const files = data.files || [];
    if (!files.length) {
      box.innerHTML = '<span class="warn">Пока нет загруженных Excel / БД</span>';
      return;
    }
    let h = '<b>Файлы на сервере</b><br>';
    files.forEach(f => {
      const when = f.mtime ? new Date(f.mtime).toLocaleString() : '';
      const url = '/admin/backup/download/' + encodeURIComponent(f.id) + '?password=' + encodeURIComponent(pw());
      h += `<div style="margin:8px 0;padding:8px;border:1px solid #e2e6ea;border-radius:8px">`;
      h += `<b>${f.label || f.name}</b> · ${fmtSize(f.size || 0)} · ${when}<br>`;
      h += `<a href="${url}">Скачать ${f.name}</a></div>`;
    });
    box.innerHTML = h;
  } catch (e) {
    box.innerHTML = '<span class="err">Сеть: ' + e + '</span>';
  }
}

document.getElementById('btnBackupList').onclick = loadBackupList;
document.getElementById('btnBackupZip').onclick = () => {
  if (!ensurePw()) return;
  window.location.href = '/admin/backup/zip?password=' + encodeURIComponent(pw());
};

renderFacultyTabs();
renderCourses();
</script>
</body>
</html>
"""
    return HTMLResponse(content=html)
