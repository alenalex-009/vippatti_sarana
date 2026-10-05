"""Password hashing with bcrypt (via passlib).

Bcrypt is a slow, salted, adaptive hash designed exactly for passwords. Every
hash embeds its own random salt, so no plaintext password is ever stored or
logged, and two users with the same password produce different hashes.
"""

from passlib.context import CryptContext

# bcrypt with automatic salt per hash; deprecated schemes kept only for verify.
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(plain: str) -> str:
    """Hash a plaintext password. The result is safe to store in the DB."""
    return pwd_context.hash(plain)


def verify_password(plain: str, password_hash: str) -> bool:
    """Constant-time check of a candidate password against a stored hash."""
    try:
        return pwd_context.verify(plain, password_hash)
    except ValueError:
        # Malformed hash in the DB — treat as a failed verification, never 500.
        return False
