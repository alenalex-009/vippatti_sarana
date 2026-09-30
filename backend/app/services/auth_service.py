"""Authentication service layer — all business rules, no HTTP details.

Split from the router so the rules are unit-testable without HTTP:
 - registration (duplicate-email rejection, hashing)
 - login (credential verification)
 - refresh-token issue/rotation/revocation (server-side session family)

Refresh tokens are persisted (by their `jti`) so logout genuinely revokes the
session instead of just telling the client to forget the token.
"""

from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.refresh_token import RefreshTokenRecord
from app.models.user import User
from app.schemas.auth import PASSWORD_MIN_LENGTH, RegisterRequest
from app.security.jwt import (
    TOKEN_TYPE_REFRESH,
    TokenError,
    create_access_token,
    create_refresh_token,
    decode_token,
)
from app.security.passwords import hash_password, verify_password


class AuthServiceError(Exception):
    """Auth business-rule failure; the router maps this to an HTTP response."""

    def __init__(self, message: str, status_code: int = 400) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code


class EmailAlreadyRegistered(AuthServiceError):
    def __init__(self) -> None:
        super().__init__("An account with this email already exists.", status_code=409)


class InvalidCredentials(AuthServiceError):
    """One generic failure for unknown email OR wrong password (no probing)."""

    def __init__(self) -> None:
        super().__init__("Incorrect email or password.", status_code=401)


class WeakPassword(AuthServiceError):
    def __init__(self) -> None:
        super().__init__(
            f"Password must be at least {PASSWORD_MIN_LENGTH} characters.", status_code=422
        )


class InvalidRefreshToken(AuthServiceError):
    def __init__(self) -> None:
        super().__init__("Refresh token is invalid or has been revoked.", status_code=401)


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def get_user_by_email(db: Session, email: str) -> User | None:
    return db.execute(
        select(User).where(User.email == email.strip().lower())
    ).scalar_one_or_none()


def get_user_by_id(db: Session, user_id: int) -> User | None:
    return db.get(User, user_id)


def register_user(db: Session, request: RegisterRequest) -> User:
    """Create a user with a hashed password. Rejects duplicate emails."""
    email = request.email.strip().lower()
    if len(request.password) < PASSWORD_MIN_LENGTH:
        raise WeakPassword()
    if get_user_by_email(db, email) is not None:
        raise EmailAlreadyRegistered()

    user = User(
        full_name=request.full_name.strip(),
        email=email,
        password_hash=hash_password(request.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def authenticate_user(db: Session, email: str, password: str) -> User:
    """Verify credentials against PostgreSQL. Raises on any mismatch."""
    user = get_user_by_email(db, email)
    if user is None:
        # Same error for unknown email and wrong password (no account probing).
        raise InvalidCredentials()
    if not verify_password(password, user.password_hash):
        raise InvalidCredentials()
    return user


def issue_session(db: Session, user: User) -> tuple[str, str, datetime]:
    """Create access + refresh tokens, persisting the refresh token server-side."""
    access_token, _ = create_access_token(user.id)
    refresh_token, jti, expires_at = create_refresh_token(user.id)
    db.add(RefreshTokenRecord(jti=jti, user_id=user.id, expires_at=expires_at))
    db.commit()
    return access_token, refresh_token, expires_at


def rotate_session(db: Session, refresh_token: str) -> tuple[User, str, str, datetime]:
    """Validate a refresh token, revoke it, and mint a fresh session family.

    Rotation means a stolen refresh token becomes useless the moment the real
    client uses it once.
    """
    record = _get_active_refresh_record(db, refresh_token)
    if record is None:
        raise InvalidRefreshToken()
    user = get_user_by_id(db, record.user_id)
    if user is None:
        raise InvalidRefreshToken()

    record.revoked_at = _utcnow()  # the old token dies exactly now
    access_token, _, _ = issue_session(db, user)
    db.commit()
    return user, access_token, refresh_token_of(db, user.id), _utcnow()


def refresh_token_of(db: Session, user_id: int) -> str:
    """Issue a fresh refresh token for [user_id] and persist it. Helper for rotate."""
    token, jti, expires_at = create_refresh_token(user_id)
    db.add(RefreshTokenRecord(jti=jti, user_id=user_id, expires_at=expires_at))
    return token


def revoke_refresh_token(db: Session, refresh_token: str) -> bool:
    """Revoke a refresh token (logout). True when an active token was revoked."""
    record = _get_active_refresh_record(db, refresh_token)
    if record is None:
        return False
    record.revoked_at = _utcnow()
    db.commit()
    return True


def _get_active_refresh_record(db: Session, refresh_token: str) -> RefreshTokenRecord | None:
    """Stored record for a refresh token, enforcing token type + revocation."""
    try:
        payload = decode_token(refresh_token, expected_type=TOKEN_TYPE_REFRESH)
    except TokenError:
        return None
    record = db.execute(
        select(RefreshTokenRecord).where(RefreshTokenRecord.jti == payload.get("jti"))
    ).scalar_one_or_none()
    if record is None or record.revoked_at is not None:
        return None
    return record


def revoke_all_for_user(db: Session, user_id: int) -> int:
    """Revoke every active refresh token of a user (logout-everywhere helper)."""
    records = db.execute(
        select(RefreshTokenRecord).where(
            RefreshTokenRecord.user_id == user_id,
            RefreshTokenRecord.revoked_at.is_(None),  # type: ignore[attr-defined]
        )
    ).scalars().all()
    now = _utcnow()
    for record in records:
        record.revoked_at = now
    db.commit()
    return len(records)
