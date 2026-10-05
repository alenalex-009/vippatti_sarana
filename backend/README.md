# Vippatti Sarana — Authentication Backend

FastAPI + PostgreSQL + SQLAlchemy + Alembic backend that owns all accounts and
sessions for the Vippatti Sarana Android app.

```
Android app  →  FastAPI backend (this folder)  →  PostgreSQL
```

The Android app never connects to PostgreSQL directly.

## Endpoints

| Method | Path                   | Auth required | Purpose                                    |
|--------|------------------------|---------------|--------------------------------------------|
| POST   | /api/v1/auth/register  | no            | Create account (full_name, email, password)|
| POST   | /api/v1/auth/login     | no            | Verify credentials, return tokens          |
| POST   | /api/v1/auth/refresh   | no (refresh)  | Rotate refresh token, new access token     |
| POST   | /api/v1/auth/logout    | yes (access)  | Revoke the session's refresh token(s)      |
| GET    | /api/v1/auth/me        | yes (access)  | Current authenticated user's real data     |

Interactive docs: `http://localhost:8000/docs` when the server runs.

## Local setup

1. **Create the database** (once):

   ```bash
   psql -h localhost -U postgres -c "CREATE DATABASE vippatti_sarana;"
   ```

2. **Configure environment** (secrets are never committed):

   ```bash
   cd backend
   copy .env.example .env        # Windows  (cp on macOS/Linux)
   # edit .env: set DATABASE_URL and JWT_SECRET_KEY
   ```

3. **Install dependencies**:

   ```bash
   python -m pip install -r requirements.txt
   ```

4. **Run migrations**:

   ```bash
   alembic upgrade head
   ```

5. **Start the API**:

   ```bash
   uvicorn app.main:app --reload --port 8000
   ```

## Running the tests

The suite uses a REAL PostgreSQL test database (schema created from the same
models, transaction-rolled-back between tests so they stay independent):

```bash
# optional: override the test DB (defaults to vippatti_sarana_test on localhost)
set TEST_DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5432/vippatti_sarana_test
python -m pytest tests -v
```

## Security notes

- Passwords: bcrypt-hashed (salted, adaptive). Plaintext is never stored.
- Access tokens: JWT, 30-minute lifetime, sent as `Authorization: Bearer ...`.
- Refresh tokens: 30-day JWT whose `jti` is persisted in `refresh_tokens`;
  logout and rotation revoke them server-side, so "logged out" is real.
- All secrets come from environment variables; `.env` is git-ignored.
