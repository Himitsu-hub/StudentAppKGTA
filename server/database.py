from datetime import datetime
import os

from dotenv import load_dotenv
from sqlalchemy import (
    create_engine,
    Column,
    Integer,
    String,
    Text,
    DateTime,
    JSON,
    UniqueConstraint,
    Index,
    text,
    inspect,
)
from sqlalchemy.orm import declarative_base, sessionmaker

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./schedule.db")

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class ScheduleRecord(Base):
    """Cached weekly schedule for a group/subgroup and week type."""

    __tablename__ = "schedules"
    __table_args__ = (
        UniqueConstraint(
            "course",
            "group_name",
            "subgroup",
            "week_type",
            name="uq_schedule_key",
        ),
        Index("ix_schedule_lookup", "course", "group_name", "subgroup", "week_type"),
    )

    id = Column(Integer, primary_key=True, index=True)
    course = Column(Integer, nullable=False)
    group_name = Column(String, nullable=False)
    subgroup = Column(String, nullable=False, default="")
    week_type = Column(String, nullable=False)  # Числитель | Знаменатель
    schedule_json = Column(JSON, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class GroupsCache(Base):
    """Cached group list per course."""

    __tablename__ = "groups_cache"

    id = Column(Integer, primary_key=True, index=True)
    course = Column(Integer, unique=True, nullable=False, index=True)
    groups_json = Column(JSON, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class UploadLog(Base):
    __tablename__ = "upload_logs"

    id = Column(Integer, primary_key=True, index=True)
    filename = Column(String, nullable=False)
    course = Column(Integer, nullable=False)
    groups_count = Column(Integer, default=0)
    lessons_count = Column(Integer, default=0)
    uploaded_at = Column(DateTime, default=datetime.utcnow)
    status = Column(String, default="success")
    error_message = Column(Text, nullable=True)


def _migrate_sqlite_schema():
    """Recreate outdated tables when schema changed (old DB without week_type)."""
    if "sqlite" not in DATABASE_URL:
        return
    try:
        insp = inspect(engine)
        if not insp.has_table("schedules"):
            return
        cols = {c["name"] for c in insp.get_columns("schedules")}
        if "week_type" in cols and "schedule_json" in cols:
            return
        print("[db] outdated schedules table — recreating")
        with engine.begin() as conn:
            conn.execute(text("DROP TABLE IF EXISTS schedules"))
            conn.execute(text("DROP TABLE IF EXISTS groups_cache"))
    except Exception as exc:
        print(f"[db] migration check failed: {exc}")


def init_db():
    _migrate_sqlite_schema()
    Base.metadata.create_all(bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
