"""Pytest fixtures: a REAL PostgreSQL test database, isolated per test.

Each test runs inside a transaction that is rolled back afterwards, so tests
never see each other's rows and the database stays clean. The schema comes
from the actual Alembic migration (which runs once per session).
"""

import os

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from app.database import get_db
from app.main import app
from app.models import refresh_token, user  # noqa: F401  (register tables)

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+psycopg://postgres:postgres@localhost:5432/vippatti_sarana_test",
)

_test_engine = create_engine(TEST_DATABASE_URL, future=True)
_TestingSession = sessionmaker(bind=_test_engine, autoflush=False, expire_on_commit=False)


@pytest.fixture(scope="session", autouse=True)
def _prepare_database():
    """Create the test database, run the real Alembic migration once."""
    admin_url = TEST_DATABASE_URL.rsplit("/", 1)[0] + "/postgres"
    db_name = TEST_DATABASE_URL.rsplit("/", 1)[1]

    admin_engine = create_engine(admin_url, isolation_level="AUTOCOMMIT")
    with admin_engine.connect() as conn:
        exists = conn.execute(
            text("SELECT 1 FROM pg_database WHERE datname = :name"), {"name": db_name}
        ).scalar()
        if not exists:
            conn.execute(text(f'CREATE DATABASE "{db_name}"'))
    admin_engine.dispose()

    # Start from a CLEAN slate, then run the ACTUAL migration (not create_all)
    # so the schema under test is exactly what Alembic produces.
    with _test_engine.connect() as conn:
        conn.execute(text("DROP TABLE IF EXISTS refresh_tokens, users, alembic_version CASCADE"))
        conn.commit()

    from alembic import command
    from alembic.config import Config

    alembic_cfg = Config("alembic.ini")
    alembic_cfg.set_main_option("sqlalchemy.url", TEST_DATABASE_URL)
    command.upgrade(alembic_cfg, "head")

    yield

    _test_engine.dispose()


@pytest.fixture
def db_session():
    """Transaction-scoped session: everything is rolled back after the test."""
    connection = _test_engine.connect()
    transaction = connection.begin()
    session = _TestingSession(bind=connection, join_transaction_mode="create_savepoint")
    yield session
    session.close()
    transaction.rollback()
    connection.close()


@pytest.fixture
def client(db_session):
    """FastAPI test client wired to the transaction-scoped session."""
    def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
