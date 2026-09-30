"""Authentication API: /api/v1/auth/* endpoints.

Registers, logs in, issues/rotates/revokes tokens and returns the
authenticated user's real information. Every error message is honest: 409 for
duplicate email, 401 for bad credentials, 422 for invalid input (Pydantic),
401 for missing/invalid/expired/revoked tokens.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.dependencies import get_current_user
from app.schemas.auth import (
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    TokenResponse,
    UserResponse,
)
from app.services import auth_service

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])
bearer_scheme = HTTPBearer(auto_error=False)


def _token_response(db: Session, user, access_token: str) -> TokenResponse:
    """Build the response body; the refresh token is minted + persisted here."""
    _, refresh_token, _ = auth_service.issue_session(db, user)
    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in=settings.access_token_expire_minutes * 60,
        user=UserResponse.model_validate(user),
    )


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(request: RegisterRequest, db: Session = Depends(get_db)) -> TokenResponse:
    """Create an account and start an authenticated session immediately."""
    try:
        user = auth_service.register_user(db, request)
    except auth_service.AuthServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from exc
    access_token, _, _ = auth_service.issue_session(db, user)
    return _token_response(db, user, access_token)


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)) -> TokenResponse:
    """Verify email + password against PostgreSQL and return tokens."""
    try:
        user = auth_service.authenticate_user(db, request.email, request.password)
    except auth_service.AuthServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from exc
    access_token, _, _ = auth_service.issue_session(db, user)
    return _token_response(db, user, access_token)


@router.post("/refresh", response_model=TokenResponse)
def refresh(body: dict, db: Session = Depends(get_db)) -> TokenResponse:
    """Exchange a valid refresh token for a fresh session (rotation)."""
    token = (body or {}).get("refresh_token", "")
    try:
        user, access_token, _new_refresh, _ = auth_service.rotate_session(db, token)
    except auth_service.AuthServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from exc
    return _token_response(db, user, access_token)


@router.post("/logout", status_code=status.HTTP_200_OK)
def logout(
    body: LogoutRequest | None = None,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    current_user=Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    """Invalidate the caller's refresh token (and any stale ones for the user).

    Requires a valid access token, so logout is itself authenticated. Without
    a refresh token in the body, every active session for this user is
    revoked — the safe default for "log me out".
    """
    refresh_token = body.refresh_token if body is not None else None
    if refresh_token:
        revoked = auth_service.revoke_refresh_token(db, refresh_token)
        if not revoked:
            # Token unknown/already revoked/expired — treat logout as idempotent.
            pass
        auth_service.revoke_all_for_user(db, current_user.id)
        return {"detail": "Logged out."}
    auth_service.revoke_all_for_user(db, current_user.id)
    return {"detail": "Logged out."}


@router.get("/me", response_model=UserResponse)
def me(current_user=Depends(get_current_user)) -> UserResponse:
    """Return the currently authenticated user's real information."""
    return UserResponse.model_validate(current_user)
