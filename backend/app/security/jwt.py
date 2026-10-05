"""JWT creation and verification.

Short-lived access tokens authenticate API calls; long-lived refresh tokens
(let the client mint a new access token) are persisted server-side so logout
can actually revoke them. Tokens carry `sub` (user id), `type`
("access"/"refresh") and `jti` (unique id used for server-side revocation).
"""

from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import uuid4

import jwt

from app.config import settings

TOKEN_TYPE_ACCESS = "access"
TOKEN_TYPE_REFRESH = "refresh"


class TokenError(Exception):
    """Raised when a token is invalid, expired or malformed."""


def _create_token(subject: str, token_type: str, expires_delta: timedelta) -> tuple[str, str]:
    now = datetime.now(timezone.utc)
    jti = uuid4().hex
    payload: dict[str, Any] = {
        "sub": subject,
        "type": token_type,
        "iat": int(now.timestamp()),
        "exp": int((now + expires_delta).timestamp()),
        "jti": jti,
    }
    encoded = jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)
    return encoded, jti


def create_access_token(user_id: int) -> tuple[str, datetime]:
    """Return (token, expires_at UTC datetime) for a short-lived access token."""
    expires_delta = timedelta(minutes=settings.access_token_expire_minutes)
    now = datetime.now(timezone.utc)
    token, _ = _create_token(str(user_id), TOKEN_TYPE_ACCESS, expires_delta)
    return token, now + expires_delta


def create_refresh_token(user_id: int) -> tuple[str, str, datetime]:
    """Return (token, jti, expires_at) for a revocable refresh token."""
    expires_delta = timedelta(days=settings.refresh_token_expire_days)
    now = datetime.now(timezone.utc)
    token, jti = _create_token(str(user_id), TOKEN_TYPE_REFRESH, expires_delta)
    return token, jti, now + expires_delta


def decode_token(token: str, expected_type: str | None = None) -> dict[str, Any]:
    """Decode and validate a JWT. Raises TokenError on any problem."""
    try:
        payload = jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
    except jwt.ExpiredSignatureError as exc:
        raise TokenError("Token has expired") from exc
    except jwt.InvalidTokenError as exc:
        raise TokenError("Token is invalid") from exc

    if expected_type is not None and payload.get("type") != expected_type:
        raise TokenError(f"Token is not a {expected_type} token")
    return payload
