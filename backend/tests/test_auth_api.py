"""End-to-end API tests for the authentication endpoints, against REAL PostgreSQL.

Covers the required contracts: registration success, duplicate email,
invalid registration data, login success, incorrect password, authenticated
/me, unauthenticated /me, logout. Nothing is mocked at the service or
database level — only the HTTP layer is exercised through FastAPI's
TestClient, and every test runs in a rolled-back transaction.
"""

import pytest

API = "/api/v1/auth"

REGISTER_BODY = {
    "full_name": "Meera Krishnan",
    "email": "meera@example.com",
    "password": "longenough7",
}


@pytest.fixture
def registered_user(client):
    """A freshly registered account; returns the token response body."""
    response = client.post(f"{API}/register", json=REGISTER_BODY)
    assert response.status_code == 201, response.text
    return response.json()


# ---------------------------------------------------------------------------
# Registration
# ---------------------------------------------------------------------------

def test_registration_success_returns_tokens_and_user(client):
    response = client.post(f"{API}/register", json=REGISTER_BODY)
    assert response.status_code == 201, response.text

    body = response.json()
    assert body["access_token"]
    assert body["refresh_token"]
    assert body["token_type"] == "bearer"
    assert body["user"]["email"] == "meera@example.com"
    assert body["user"]["full_name"] == "Meera Krishnan"
    # A hash must never leak.
    assert "password" not in body["user"]
    assert "password_hash" not in body["user"]


def test_duplicate_email_rejected_with_409(client, registered_user):
    response = client.post(f"{API}/register", json=REGISTER_BODY)
    assert response.status_code == 409
    assert "already exists" in response.json()["detail"].lower()


def test_duplicate_email_case_insensitive(client, registered_user):
    response = client.post(
        f"{API}/register",
        json={
            "full_name": "Other Person",
            "email": "  MEERA@example.com ",  # same account, different casing
            "password": "longenough7",
        },
    )
    assert response.status_code == 409


@pytest.mark.parametrize(
    "payload",
    [
        # missing / blank name
        {"full_name": "", "email": "x1@example.com", "password": "longenough7"},
        {"full_name": "   ", "email": "x2@example.com", "password": "longenough7"},
        # invalid email
        {"full_name": "X", "email": "not-an-email", "password": "longenough7"},
        {"full_name": "X", "email": "", "password": "longenough7"},
        # password too short
        {"full_name": "X", "email": "x3@example.com", "password": "short"},
        {"full_name": "X", "email": "x4@example.com", "password": ""},
    ],
)
def test_invalid_registration_data_rejected_422(client, payload):
    response = client.post(f"{API}/register", json=payload)
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# Login
# ---------------------------------------------------------------------------

def test_login_success_returns_tokens(client, registered_user):
    response = client.post(
        f"{API}/login",
        json={"email": "meera@example.com", "password": "longenough7"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["access_token"]
    assert body["refresh_token"]
    assert body["user"]["email"] == "meera@example.com"
    assert body["user"]["full_name"] == "Meera Krishnan"


def test_wrong_password_rejected_401(client, registered_user):
    response = client.post(
        f"{API}/login",
        json={"email": "meera@example.com", "password": "wrongpassword1"},
    )
    assert response.status_code == 401
    assert "incorrect" in response.json()["detail"].lower()


def test_unknown_email_rejected_401(client):
    response = client.post(
        f"{API}/login",
        json={"email": "ghost@example.com", "password": "whatever123"},
    )
    assert response.status_code == 401


def test_malformed_login_body_422(client):
    response = client.post(f"{API}/login", json={"email": "nope"})
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# /me
# ---------------------------------------------------------------------------

def test_me_returns_authenticated_user(client, registered_user):
    token = registered_user["access_token"]
    response = client.get(f"{API}/me", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["email"] == "meera@example.com"
    assert body["full_name"] == "Meera Krishnan"
    assert body["id"] == registered_user["user"]["id"]
    assert "created_at" in body and "updated_at" in body


def test_me_without_token_is_401(client):
    response = client.get(f"{API}/me")
    assert response.status_code == 401


def test_me_with_garbage_token_is_401(client):
    response = client.get(f"{API}/me", headers={"Authorization": "Bearer not-a-jwt"})
    assert response.status_code == 401


def test_me_with_tampered_token_is_401(client, registered_user):
    token = registered_user["access_token"]
    tampered = token[:-4] + "AAAA"
    response = client.get(f"{API}/me", headers={"Authorization": f"Bearer {tampered}"})
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# Logout
# ---------------------------------------------------------------------------

def test_logout_revokes_session_and_me_still_needs_new_login(client, registered_user):
    access = registered_user["access_token"]
    refresh = registered_user["refresh_token"]

    response = client.post(
        f"{API}/logout",
        json={"refresh_token": refresh},
        headers={"Authorization": f"Bearer {access}"},
    )
    assert response.status_code == 200, response.text
    assert "logged out" in response.json()["detail"].lower()

    # The refresh token must now be REJECTED (server-side revocation).
    refreshed = client.post(f"{API}/refresh", json={"refresh_token": refresh})
    assert refreshed.status_code == 401


def test_logout_without_refresh_revokes_all_sessions(client, registered_user):
    access = registered_user["access_token"]
    response = client.post(
        f"{API}/logout", json=None, headers={"Authorization": f"Bearer {access}"}
    )
    assert response.status_code == 200


def test_logout_requires_authentication(client):
    response = client.post(f"{API}/logout", json={})
    assert response.status_code == 401


def test_logout_is_idempotent(client, registered_user):
    access = registered_user["access_token"]
    refresh = registered_user["refresh_token"]
    first = client.post(
        f"{API}/logout", json={"refresh_token": refresh},
        headers={"Authorization": f"Bearer {access}"},
    )
    assert first.status_code == 200
    second = client.post(
        f"{API}/logout", json={"refresh_token": refresh},
        headers={"Authorization": f"Bearer {access}"},
    )
    # Already-revoked token: still a successful, honest logout.
    assert second.status_code == 200


# ---------------------------------------------------------------------------
# Refresh / session handling
# ---------------------------------------------------------------------------

def test_refresh_rotates_and_old_token_dies(client, registered_user):
    refresh = registered_user["refresh_token"]
    response = client.post(f"{API}/refresh", json={"refresh_token": refresh})
    assert response.status_code == 200, response.text
    new_tokens = response.json()
    assert new_tokens["access_token"]
    assert new_tokens["refresh_token"]

    # The OLD refresh token was rotated away — replaying it must fail.
    replay = client.post(f"{API}/refresh", json={"refresh_token": refresh})
    assert replay.status_code == 401


def test_refresh_with_garbage_token_401(client):
    response = client.post(f"{API}/refresh", json={"refresh_token": "garbage"})
    assert response.status_code == 401


def test_access_token_cannot_refresh(client, registered_user):
    """A stolen access token must NOT work as a refresh token."""
    access = registered_user["access_token"]
    response = client.post(f"{API}/refresh", json={"refresh_token": access})
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# Database reality checks (rows actually in PostgreSQL, password hashed)
# ---------------------------------------------------------------------------

def test_user_row_exists_in_postgres_with_hashed_password(client, db_session):
    from sqlalchemy import select

    from app.models.user import User

    created = client.post(f"{API}/register", json=REGISTER_BODY)
    assert created.status_code == 201

    row = db_session.execute(
        select(User).where(User.email == "meera@example.com")
    ).scalar_one()
    assert row.full_name == "Meera Krishnan"
    # NEVER plaintext: bcrypt hashes start with $2 and contain no "longenough7".
    assert row.password_hash.startswith("$2")
    assert "longenough7" not in row.password_hash
    assert row.created_at is not None
    assert row.updated_at is not None
