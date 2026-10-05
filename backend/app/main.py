"""Vippatti Sarana authentication backend — FastAPI application entry point.

Run locally (from backend/):
    uvicorn app.main:app --reload --port 8000

Architecture: Android app -> this FastAPI backend -> PostgreSQL. The mobile
client NEVER touches the database directly.
"""

import warnings

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import auth

app = FastAPI(
    title="Vippatti Sarana Auth API",
    version="1.0.0",
    description="Real token-based authentication for the Vippatti Sarana disaster-relief app.",
)

if settings.cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(auth.router)


@app.get("/", tags=["meta"])
def root() -> dict:
    """Liveness probe."""
    return {"app": "Vippatti Sarana Auth API", "status": "ok", "docs": "/docs"}
