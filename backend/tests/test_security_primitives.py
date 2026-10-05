"""Unit tests for the security primitives (no HTTP, no database)."""

from app.security.jwt import (
    TOKEN_TYPE_ACCESS,
    TOKEN_TYPE_REFRESH,
    TokenError,
    create_access_token,
    create_refresh_token,
    decode_token,
)
from app.security.passwords import hash_password, verify_password


class TestPasswords:
    def test_hash_is_salted_bcrypt_and_not_reversible(self):
        hashed = hash_password("longenough7")
        assert hashed.startswith("$2")
        assert "longenough7" not in hashed

    def test_same_password_two_hashes_different_salts(self):
        first = hash_password("longenough7")
        second = hash_password("longenough7")
        assert first != second  # per-hash salt

    def test_verify_accepts_correct_and_rejects_wrong(self):
        hashed = hash_password("longenough7")
        assert verify_password("longenough7", hashed)
        assert not verify_password("wrong", hashed)

    def test_verify_on_malformed_hash_is_false_not_crash(self):
        assert not verify_password("x", "not-a-hash")


class TestJwt:
    def test_access_token_roundtrip(self):
        token, expires_at = create_access_token(user_id=42)
        payload = decode_token(token, expected_type=TOKEN_TYPE_ACCESS)
        assert payload["sub"] == "42"
        assert payload["type"] == TOKEN_TYPE_ACCESS
        assert payload["jti"]
        assert expires_at is not None

    def test_refresh_token_has_unique_jti(self):
        token_a, jti_a, _ = create_refresh_token(user_id=1)
        token_b, jti_b, _ = create_refresh_token(user_id=1)
        assert jti_a != jti_b
        assert decode_token(token_a, expected_type=TOKEN_TYPE_REFRESH)["type"] == TOKEN_TYPE_REFRESH

    def test_expired_token_rejected(self, monkeypatch):
        from app.config import settings

        monkeypatch.setattr(settings, "access_token_expire_minutes", -1)
        token, _ = create_access_token(user_id=1)
        try:
            decode_token(token, expected_type=TOKEN_TYPE_ACCESS)
            raise AssertionError("expired token must be rejected")
        except TokenError:
            pass

    def test_wrong_type_rejected(self):
        token, _ = create_access_token(user_id=1)
        try:
            decode_token(token, expected_type=TOKEN_TYPE_REFRESH)
            raise AssertionError("access token must not verify as refresh")
        except TokenError:
            pass

    def test_garbage_token_rejected(self):
        try:
            decode_token("garbage")
            raise AssertionError("garbage must be rejected")
        except TokenError:
            pass
