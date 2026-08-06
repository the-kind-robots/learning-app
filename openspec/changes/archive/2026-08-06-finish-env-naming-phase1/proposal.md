# Finish LEARNING_APP__ env naming — phase 1, the safe three

## Why

App envs follow `LEARNING_APP__XXX` (double underscore), stated in review of #209, where `LEARNING_APP__DB_PATH` was renamed to match. Three names still use the old single form and are safe to rename in one step; the mixed convention invites the next misconfiguration.

## What Changes

- `LEARNING_APP_PORT` → `LEARNING_APP__PORT`: read in `src/backend/core.clj` only; nothing in infra sets it, the default covers the gap. Also renamed in `test/browser/README.md`, which sets it for the suite's own backend.
- `LEARNING_APP_DB_BACKUP_PATH` → `LEARNING_APP__DB_BACKUP_PATH`: set in the backup unit `Environment=`, read in `backup.sh`; both ship in the same deb, so the rename is atomic within one artifact.
- `LEARNING_APP_DB_PATH` → `LEARNING_APP__DB_PATH` in `backup.sh` (`:=` default, export, sqlite3 use) — the conf side was already renamed in #209; the script now picks up the conf value again.
- **Out of scope**: `LEARNING_APP_DB_AUTH_SECRET`/`_PATH` — spans two deploy artifacts (unit in the deb, reader in the jar); phase 2, gated on staging rung 1 (#214).

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**: `backend-configuration` — ADDED requirement recording the naming convention.

## Impact

`src/backend/core.clj`, `infra/production/etc/systemd/system/learning-app-backup-db.service`, `infra/production/usr/share/learning-app/backup.sh`, `test/browser/README.md`. No behavior change when the env is unset — defaults are identical.
