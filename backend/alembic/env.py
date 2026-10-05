"""Environment configuration for Alembic migrations.

Reads DATABASE_URL from the environment / backend/.env so no credential is
ever committed. Target metadata comes from the app models.
"""

import os
import sys
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

# Make `app` importable when alembic runs from backend/.
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from app.config import settings  # noqa: E402
from app.database import Base  # noqa: E402
from app.models import refresh_token, user  # noqa: E402,F401  (register tables)

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# The single source of truth for the database URL: the caller-supplied
# sqlalchemy.url (the test suite overrides this) when set, otherwise the
# environment / backend/.env via app.config.settings.
if config.get_main_option("sqlalchemy.url") in (None, "", "driver://user@/"):
    config.set_main_option("sqlalchemy.url", settings.database_url)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """Emit SQL to stdout without a live DB connection."""
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations against the configured database."""
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


run_migrations_offline() if context.is_offline_mode() else run_migrations_online()
