"""FastAPI dependency that resolves the current authenticated user.

A request is authenticated when it carries `Authorization: Bearer <token>`
with a VALID, UNEXPIRED access JWT. The token is stateless; the user row is
loaded from PostgreSQL so /me returns real account data.
"""

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.database import get_db
from app.security.jwt import TOKEN_TYPE_ACCESS, TokenError, decode_token
from app.services import auth_service

bearer_scheme = HTTPBearer(auto_error=False)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
):
    """Resolve the authenticated user or raise 401. No anonymous fallback."""
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Not authenticated",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if credentials is None or not credentials.credentials:
        raise unauthorized
    try:
        payload = decode_token(credentials.credentials, expected_type=TOKEN_TYPE_ACCESS)
    except TokenError as exc:
        raise unauthorized from exc

    subject = payload.get("sub")
    if subject is None or not str(subject).isdigit():
        raise unauthorized
    user = auth_service.get_user_by_id(db, int(subject))
    if user is None:
        # Token signed correctly but the account no longer exists.
        raise unauthorized
    return user
