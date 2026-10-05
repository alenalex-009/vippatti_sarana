"""Application configuration, loaded strictly from environment variables.

No secret is ever hardcoded: every value below comes from the environment
(12-factor), with a `.env` file supported for local development. Copy
`.env.example` to `.env` and fill in real values.
"""

from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All runtime configuration. Constructed once via get_settings()."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- Database -----------------------------------------------------------
    database_url: str = Field(
        default="postgresql+psycopg://postgres:postgres@localhost:5432/vippatti_sarana",
        description="SQLAlchemy URL.postgresql+psycopg://user:password@host:port/dbname",
    )

    # ---- JWT ----------------------------------------------------------------
    jwt_secret_key: str = Field(default="CHANGE-ME-generate-a-real-secret")
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30

    # ---- CORS ---------------------------------------------------------------
    backend_cors_origins_raw: str = Field(default="", alias="BACKEND_CORS_ORIGINS")

    @field_validator("jwt_secret_key")
    @classmethod
    def warn_on_default_secret(cls, value: str) -> str:
        if value.startswith("CHANGE-ME"):
            # Not a hard failure so the test/dev environment still boots, but
            # production MUST set a real secret; the server logs a warning.
            import warnings

            warnings.warn(
                "JWT_SECRET_KEY is still the placeholder — generate a real secret "
                "before any real deployment.",
                stacklevel=1,
            )
        return value

    @property
    def cors_origins(self) -> list[str]:
        raw = self.backend_cors_origins_raw.strip().strip("[]")
        return [origin.strip().strip('"').strip("'") for origin in raw.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    """Cached settings instance — import this, never construct Settings directly."""
    return Settings()


settings = get_settings()
