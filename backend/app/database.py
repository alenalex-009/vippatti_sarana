"""SQLAlchemy engine, session factory and declarative base.

The ONLY place in the whole system that knows how to reach PostgreSQL. The
Android app never talks to the database directly — it goes through this
backend's HTTP API (FastAPI), which owns these sessions.
"""

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings

engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    future=True,
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, future=True)


class Base(DeclarativeBase):
    """Declarative base for every ORM model."""


def get_db() -> Generator[Session, None, None]:
    """FastAPI dependency yielding a scoped database session per request."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
