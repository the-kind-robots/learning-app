# Configurable database path

## Why

`db-spec` in `src/backend/core.clj` hardcodes `app.db` relative to the working directory (GH-192). Any process started from another directory — a worktree, a REPL, a one-off tool — silently creates a fresh empty database wherever it happens to run, instead of failing or finding the real one. The port already reads `LEARNING_APP_PORT`; the database path deserves the same escape hatch.

## What changes

- `db-spec` reads `LEARNING_APP_DB_PATH`, defaulting to `app.db` — production behavior (WorkingDirectory=/opt/learning-app, so /opt/learning-app/app.db) is unchanged.
- `db-spec` stays the single home of the value: migrations and handlers already take it as an argument; dictionary tools and tests build their own specs for their own files and are untouched.

## Impact

Added: `backend-configuration`. Files: `src/backend/core.clj`.
