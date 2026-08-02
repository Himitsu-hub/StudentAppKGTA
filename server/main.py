import os
import json
import secrets
import shutil
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
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

load_dotenv()

ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "").strip()
if not ADMIN_PASSWORD:
    # Dev fallback only — production must set ADMIN_PASSWORD in .env
    ADMIN_PASSWORD = "changeme-dev-only"
    print("WARNING: ADMIN_PASSWORD is not set. Using insecure default.")

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
TEACHERS_FILE = Path("teachers.json")
WEEK_TYPES = ("Числитель", "Знаменатель")

app = FastAPI(
    title="StudentApp Schedule API",
    version="2.0.0",
    description="API расписания, новостей и преподавателей КГТА",
)

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


@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}


@app.get("/api/teachers")
def get_teachers():
    if not TEACHERS_FILE.exists():
        return {"teachers": []}
    with open(TEACHERS_FILE, "r", encoding="utf-8") as f:
        teachers = json.load(f)
    return {"teachers": teachers}


@app.get("/api/news")
def get_news(limit: int = Query(10, ge=1, le=50)):
    """Live scrape from dksta.ru; on success overwrites news_cache.json."""
    from news_scraper import scrape_news

    news = scrape_news(limit)
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


@app.get("/api/groups")
def get_groups(course: int = Query(..., ge=1, le=4), db: Session = Depends(get_db)):
    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"

    # Prefer DB cache, but never fail hard — Excel is always a fallback
    try:
        ensure_course_indexed(db, course)
        cached = db.query(GroupsCache).filter(GroupsCache.course == course).first()
        if cached and cached.groups_json:
            return cached.groups_json
    except Exception as exc:
        print(f"[groups] index/cache error course={course}: {exc}")
        try:
            db.rollback()
        except Exception:
            pass

    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"Schedule for course {course} not found")

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


@app.post("/admin/upload")
async def upload_schedule(
    course: int = Form(..., ge=1, le=4),
    file: UploadFile = File(...),
    password: str = Form(...),
    db: Session = Depends(get_db),
):
    verify_admin_password(password)

    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(status_code=400, detail="Only Excel files are accepted")

    file_path = UPLOAD_DIR / f"schedule{course}.xlsx"
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    try:
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
        }
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


@app.get("/admin", response_class=HTMLResponse)
async def admin_panel():
    """Admin UI — password is entered in the form, never in the URL."""
    html = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>StudentApp Admin</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; background: #f4f6f8; color: #1a1a1a; }
        h1 { color: #1a336c; }
        .card { background: #fff; padding: 20px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.08); margin: 20px 0; }
        label { display: block; margin: 12px 0 6px; font-weight: 600; }
        select, input { padding: 10px; width: 100%; box-sizing: border-box; border: 1px solid #ccd; border-radius: 8px; }
        button { background: #1a336c; color: white; padding: 12px 20px; border: none; border-radius: 8px; cursor: pointer; margin-top: 16px; font-weight: 600; }
        button:hover { background: #142650; }
        table { width: 100%; border-collapse: collapse; margin-top: 12px; }
        th, td { border: 1px solid #e2e6ea; padding: 10px; text-align: left; font-size: 14px; }
        th { background: #1a336c; color: white; }
        .ok { color: #1b7a3d; } .err { color: #b42318; }
        #result { margin-top: 12px; }
    </style>
</head>
<body>
    <h1>StudentApp Admin</h1>
    <div class="card">
        <h2>Загрузка расписания</h2>
        <form id="uploadForm">
            <label>Курс</label>
            <select id="course">
                <option value="1">1 курс</option>
                <option value="2">2 курс</option>
                <option value="3">3 курс</option>
                <option value="4">4 курс</option>
            </select>
            <label>Excel-файл (.xlsx)</label>
            <input type="file" id="file" accept=".xlsx,.xls" required>
            <label>Пароль администратора</label>
            <input type="password" id="password" required autocomplete="current-password">
            <button type="submit">Загрузить и проиндексировать</button>
        </form>
        <div id="result"></div>
    </div>
    <div class="card">
        <h2>История загрузок</h2>
        <button type="button" id="refreshBtn">Обновить историю</button>
        <table>
            <thead><tr><th>Дата</th><th>Файл</th><th>Курс</th><th>Групп</th><th>Пар</th><th>Статус</th></tr></thead>
            <tbody id="historyBody"></tbody>
        </table>
    </div>
    <script>
        async function loadHistory() {
            const pw = document.getElementById('password').value;
            if (!pw) return;
            const res = await fetch('/admin/status', {
                headers: { 'X-Admin-Password': pw }
            });
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
        document.getElementById('refreshBtn').addEventListener('click', loadHistory);
        document.getElementById('uploadForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const formData = new FormData();
            formData.append('course', document.getElementById('course').value);
            formData.append('file', document.getElementById('file').files[0]);
            formData.append('password', document.getElementById('password').value);
            const div = document.getElementById('result');
            div.textContent = 'Загрузка...';
            try {
                const res = await fetch('/admin/upload', { method: 'POST', body: formData });
                const data = await res.json();
                if (res.ok) {
                    div.innerHTML = `<p class="ok">Готово. Групп: ${data.groups_count}, пар: ${data.lessons_count}</p>`;
                    loadHistory();
                } else {
                    div.innerHTML = `<p class="err">Ошибка: ${data.detail || res.status}</p>`;
                }
            } catch (err) {
                div.innerHTML = '<p class="err">Сетевая ошибка</p>';
            }
        });
    </script>
</body>
</html>
"""
    return HTMLResponse(content=html)
