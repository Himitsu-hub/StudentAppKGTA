import os
import io
import json
import secrets
import shutil
import tempfile
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
from parser import (
    get_current_week_type,
    get_groups_from_file,
    parse_schedule_for_group,
    schedule_to_dict,
)
from schedule_validator import validate_schedule_file

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
    if not password or not secrets.compare_digest(password, ADMIN_PASSWORD):
        raise HTTPException(status_code=401, detail="Invalid admin password")


def schedule_days_from_json(raw: list) -> list[dict]:
    return raw if isinstance(raw, list) else []


def index_course_file(db: Session, course: int, filename: str = "") -> tuple[int, int]:
    """Parse Excel for a course and cache all groups × week types in SQLite."""
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
    if not file_path.exists():
        raise FileNotFoundError(f"Schedule for course {course} not found")

    groups = get_groups_from_file(str(file_path))
    total_lessons = 0

    db.query(ScheduleRecord).filter(ScheduleRecord.course == course).delete()
    db.query(GroupsCache).filter(GroupsCache.course == course).delete()

    db.add(GroupsCache(course=course, groups_json=groups))

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
                        course=course,
                        group_name=group_name,
                        subgroup=sub or "",
                        week_type=week_type,
                        schedule_json=schedule_to_dict(schedule),
                    )
                )

    db.commit()
    return len(groups), total_lessons


def ensure_course_indexed(db: Session, course: int, force: bool = False) -> None:
    """Cache Excel → JSON in SQLite. force=True rebuilds (needed after parser updates)."""
    if not force:
        exists = (
            db.query(ScheduleRecord.id)
            .filter(ScheduleRecord.course == course)
            .limit(1)
            .first()
        )
        if exists:
            return
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
    if file_path.exists():
        index_course_file(db, course)


def _news_refresh_loop(interval_sec: int = 20 * 60) -> None:
    """Background: re-scrape dksta.ru every 20 minutes so cache stays fresh."""
    import threading
    import time

    from news_scraper import scrape_news

    def worker() -> None:
        while True:
            try:
                n = scrape_news(20)
                print(f"[news-loop] refreshed {len(n)} items")
            except Exception as exc:
                print(f"[news-loop] error: {exc}")
            time.sleep(interval_sec)

    t = threading.Thread(target=worker, name="news-refresh", daemon=True)
    t.start()


@app.on_event("startup")
def startup():
    init_db()
    db = next(get_db())
    try:
        # Always re-parse on startup so fixes in parser.py are reflected in JSON cache
        for course in range(1, 5):
            try:
                ensure_course_indexed(db, course, force=True)
            except Exception as exc:
                print(f"Startup index course {course}: {exc}")
    finally:
        db.close()

    # Warm news cache immediately + keep refreshing in background
    try:
        from news_scraper import scrape_news

        scrape_news(20)
    except Exception as exc:
        print(f"[news] startup scrape: {exc}")
    _news_refresh_loop(20 * 60)


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


@app.get("/api/teachers")
def get_teachers():
    if not TEACHERS_FILE.exists():
        return {"teachers": []}
    with open(TEACHERS_FILE, "r", encoding="utf-8") as f:
        teachers = json.load(f)
    if isinstance(teachers, list):
        for t in teachers:
            if isinstance(t, dict) and "position" in t:
                t["position"] = _normalize_teacher_position(str(t.get("position") or ""))
    return {"teachers": teachers}


@app.get("/api/news")
def get_news(limit: int = Query(15, ge=1, le=50)):
    """Live scrape from dksta.ru; merges into news_cache.json (never stuck on old posts)."""
    from news_scraper import load_cached_news, scrape_news

    # Prefer a fresh scrape; on failure still return whatever is in cache
    try:
        news = scrape_news(limit)
    except Exception as exc:
        print(f"[news] api scrape failed: {exc}")
        news = load_cached_news()[:limit]
    return {"news": news, "count": len(news)}


@app.get("/api/courses")
def get_courses(db: Session = Depends(get_db)):
    courses = []
    for i in range(1, 5):
        f = UPLOAD_DIR / f"schedule{i}.xlsx"
        cached = (
            db.query(ScheduleRecord.id)
            .filter(ScheduleRecord.course == i)
            .limit(1)
            .first()
            is not None
        )
        courses.append(
            {
                "course": i,
                "available": f.exists() or cached,
            }
        )
    return courses


@app.get("/api/week-type")
def week_type():
    return {"weekType": get_current_week_type()}


def _course_file_version(course: int) -> Optional[dict]:
    """
    Stable version for a course schedule file.
    Changes only when the Excel on disk is replaced (mtime/size),
    not when the server merely re-indexes JSON cache.
    """
    path = UPLOAD_DIR / f"schedule{course}.xlsx"
    if not path.exists():
        return None
    try:
        st = path.stat()
    except OSError:
        return None
    version = f"{int(st.st_mtime)}-{st.st_size}"
    from datetime import datetime, timezone

    updated_at = datetime.fromtimestamp(st.st_mtime, tz=timezone.utc).isoformat()
    return {
        "course": course,
        "version": version,
        "updatedAt": updated_at,
        "available": True,
    }


@app.get("/api/schedule-updates")
def schedule_updates():
    """
    Lightweight endpoint for the app to detect new Excel uploads.
    Clients poll this (e.g. every 15 min) and show a push-style notification
    when their course version changes.
    """
    courses = []
    for i in range(1, 5):
        info = _course_file_version(i)
        if info is None:
            courses.append(
                {
                    "course": i,
                    "version": "",
                    "updatedAt": None,
                    "available": False,
                }
            )
        else:
            courses.append(info)
    # Single fingerprint of all available courses
    fingerprint = "|".join(
        f"{c['course']}:{c['version']}" for c in courses if c.get("version")
    )
    return {
        "courses": courses,
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
def get_groups(course: int = Query(..., ge=1, le=4), db: Session = Depends(get_db)):
    """
    Always re-read groups from Excel with the current parser when the file exists.
    (Old SQLite GroupsCache often kept a stale list, e.g. И-125 with only 1 subgroup.)
    """
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"

    if file_path.exists():
        try:
            groups = get_groups_from_file(str(file_path))
        except Exception as exc:
            raise HTTPException(status_code=500, detail=f"Parse error: {exc}") from exc

        try:
            existing = db.query(GroupsCache).filter(GroupsCache.course == course).first()
            if existing:
                existing.groups_json = groups
            else:
                db.add(GroupsCache(course=course, groups_json=groups))
            db.commit()
        except Exception as exc:
            print(f"[groups] cache write failed: {exc}")
            try:
                db.rollback()
            except Exception:
                pass

        return groups

    # No Excel on disk — last resort: SQLite cache
    try:
        cached = db.query(GroupsCache).filter(GroupsCache.course == course).first()
        if cached and cached.groups_json:
            return cached.groups_json
    except Exception as exc:
        print(f"[groups] cache read error course={course}: {exc}")
        try:
            db.rollback()
        except Exception:
            pass

    raise HTTPException(status_code=404, detail=f"Schedule for course {course} not found")


@app.get("/api/schedule")
def get_schedule(
    course: int = Query(..., ge=1, le=4),
    group: str = Query(...),
    subgroup: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    week = get_current_week_type()
    subgroup_key = subgroup or ""
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"

    record = None
    try:
        ensure_course_indexed(db, course)
        record = (
            db.query(ScheduleRecord)
            .filter(
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
            "course": course,
            "group": group,
            "subgroup": subgroup,
            "weekType": week,
            "fromCache": True,
            "schedule": schedule_days_from_json(record.schedule_json),
        }

    # Last resort: live parse from Excel
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"Schedule for course {course} not found")

    schedule = parse_schedule_for_group(str(file_path), group, subgroup, week_type=week)
    return {
        "course": course,
        "group": group,
        "subgroup": subgroup,
        "weekType": week,
        "fromCache": False,
        "schedule": schedule_to_dict(schedule),
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
    course: int = Form(..., ge=1, le=4),
    file: UploadFile = File(...),
    password: str = Form(...),
    db: Session = Depends(get_db),
    skip_validate: str = Form("false"),
):
    verify_admin_password(password)

    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(status_code=400, detail="Only Excel files are accepted")

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

        file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
        shutil.copy2(tmp_path, file_path)

        total_groups, total_lessons = index_course_file(db, course, file.filename)
        log = UploadLog(
            filename=file.filename,
            course=course,
            groups_count=total_groups,
            lessons_count=total_lessons,
            status="success",
        )
        db.add(log)
        db.commit()
        return {
            "status": "success",
            "filename": file.filename,
            "course": course,
            "groups_count": total_groups,
            "lessons_count": total_lessons,
            "validation": validation,
        }
    except HTTPException:
        raise
    except Exception as e:
        log = UploadLog(
            filename=file.filename,
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


@app.get("/admin/status")
def admin_status(
    x_admin_password: Optional[str] = Header(None, alias="X-Admin-Password"),
    password: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    verify_admin_password(x_admin_password or password or "")

    logs = db.query(UploadLog).order_by(UploadLog.uploaded_at.desc()).limit(10).all()
    return {
        "uploads": [
            {
                "filename": log.filename,
                "course": log.course,
                "groups_count": log.groups_count,
                "lessons_count": log.lessons_count,
                "uploaded_at": log.uploaded_at.isoformat() if log.uploaded_at else None,
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
</style>
</head>
<body>
  <h1>Расписание → приложение</h1>
  <p class="sub">Для сотрудника, который готовит Excel. Пароль один раз, файлы по курсам — проверить, потом опубликовать. SSH и консоль не нужны.</p>

  <div class="card">
    <label>Пароль администратора</label>
    <input type="password" id="password" autocomplete="current-password" placeholder="Пароль из .env сервера">
    <p class="hint">Пароль не попадает в адресную строку. Сохраните его только у УМУ / ответственного.</p>
  </div>

  <div class="card">
    <h2 style="margin-top:0;color:var(--blue);font-size:1.15rem;">Файлы по курсам</h2>
    <p class="hint">1) Выберите Excel → 2) «Проверить» → 3) «Опубликовать» (или «Опубликовать все проверенные»).</p>
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
      <thead><tr><th>Дата</th><th>Файл</th><th>Курс</th><th>Групп</th><th>Пар</th><th>Статус</th></tr></thead>
      <tbody id="historyBody"></tbody>
    </table>
  </div>

<script>
const state = {}; // course -> { file, validation, reportEl }

function pw() { return document.getElementById('password').value || ''; }

function ensurePw() {
  if (!pw()) { alert('Сначала введите пароль администратора'); return false; }
  return true;
}

function renderCourses() {
  const root = document.getElementById('courses');
  root.innerHTML = '';
  for (let c = 1; c <= 4; c++) {
    const box = document.createElement('div');
    box.className = 'course-box';
    box.innerHTML = `
      <h3>${c} курс</h3>
      <input type="file" id="file${c}" accept=".xlsx,.xls">
      <div class="btns">
        <button type="button" class="secondary" data-act="validate" data-c="${c}">Проверить</button>
        <button type="button" data-act="publish" data-c="${c}">Опубликовать</button>
      </div>
      <div class="report" id="rep${c}" style="display:none;"></div>`;
    root.appendChild(box);
    state[c] = { validation: null };
  }
  root.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', () => {
      const c = +btn.dataset.c;
      if (btn.dataset.act === 'validate') validateOne(c);
      else publishOne(c);
    });
  });
}

function showReport(c, html, kind) {
  const el = document.getElementById('rep' + c);
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

async function validateOne(c) {
  if (!ensurePw()) return;
  const f = document.getElementById('file' + c).files[0];
  if (!f) { alert('Выберите файл для ' + c + ' курса'); return; }
  showReport(c, 'Проверка…', '');
  const fd = new FormData();
  fd.append('password', pw());
  fd.append('file', f);
  fd.append('course', c);
  try {
    const res = await fetch('/admin/validate', { method: 'POST', body: fd });
    const data = await res.json();
    if (!res.ok) {
      showReport(c, '<span class="err">' + (data.detail || res.status) + '</span>', 'err');
      state[c].validation = null;
      return;
    }
    state[c].validation = data;
    state[c].file = f;
    showReport(c, formatValidation(data), data.ok ? 'ok' : 'err');
  } catch (e) {
    showReport(c, '<span class="err">Сеть: ' + e + '</span>', 'err');
  }
}

async function publishOne(c, force) {
  if (!ensurePw()) return;
  const f = document.getElementById('file' + c).files[0];
  if (!f) { alert('Выберите файл для ' + c + ' курса'); return; }
  if (!state[c].validation && !force) {
    const go = confirm('Файл ещё не проверен. Сначала проверить, потом опубликовать?\\nОК = только проверить, Отмена = отмена.');
    if (go) { await validateOne(c); return; }
    return;
  }
  if (state[c].validation && (state[c].validation.warnings||[]).length && !force) {
    if (!confirm('Есть предупреждения. Всё равно опубликовать ' + c + ' курс?')) return;
  }
  showReport(c, 'Публикация…', '');
  const fd = new FormData();
  fd.append('password', pw());
  fd.append('file', f);
  fd.append('course', c);
  if (force) fd.append('skip_validate', 'true');
  try {
    const res = await fetch('/admin/upload', { method: 'POST', body: fd });
    const data = await res.json();
    if (res.ok) {
      let h = `<div class="ok"><b>Опубликовано</b> · групп: ${data.groups_count}, пар: ${data.lessons_count}</div>`;
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
    showReport(c, '<span class="err">Сеть: ' + e + '</span>', 'err');
  }
}

document.getElementById('btnValidateAll').onclick = async () => {
  for (let c = 1; c <= 4; c++) {
    if (document.getElementById('file' + c).files[0]) await validateOne(c);
  }
};
document.getElementById('btnPublishAll').onclick = async () => {
  if (!ensurePw()) return;
  if (!confirm('Опубликовать все курсы, для которых выбран файл?')) return;
  for (let c = 1; c <= 4; c++) {
    if (document.getElementById('file' + c).files[0]) await publishOne(c, true);
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
    tr.innerHTML = `<td>${u.uploaded_at ? new Date(u.uploaded_at).toLocaleString() : ''}</td>
      <td>${u.filename || ''}</td><td>${u.course}</td><td>${u.groups_count}</td>
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

renderCourses();
</script>
</body>
</html>
"""
    return HTMLResponse(content=html)
