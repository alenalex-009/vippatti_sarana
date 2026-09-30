"""Request/response schemas for the authentication API.

Validation lives here so the routers stay thin: Pydantic rejects malformed
input before any service or database code runs.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

PASSWORD_MIN_LENGTH = 8


class RegisterRequest(BaseModel):
    """POST /api/v1/auth/register body."""

    full_name: str = Field(min_length=1, max_length=120)
    email: EmailStr
    password: str = Field(min_length=PASSWORD_MIN_LENGTH, max_length=128)

    @field_validator("full_name")
    @classmethod
    def name_not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("full_name must not be blank")
        return stripped


class LoginRequest(BaseModel):
    """POST /api/v1/auth/login body."""

    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class LogoutRequest(BaseModel):
    """POST /api/v1/auth/logout body — the refresh token to revoke (optional).

    An access token alone is enough to log out; sending the refresh token lets
    the server revoke it too, ending the whole session family.
    """

    refresh_token: str | None = None


class UserResponse(BaseModel):
    """The authenticated user's real information (GET /api/v1/auth/me)."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    full_name: str
    email: str
    created_at: datetime
    updated_at: datetime


class TokenResponse(BaseModel):
    """Tokens returned by register and login."""

    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int  # access-token lifetime in seconds
    user: UserResponse
