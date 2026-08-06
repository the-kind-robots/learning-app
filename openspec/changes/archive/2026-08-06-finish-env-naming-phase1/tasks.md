# Tasks

## 1. Renames

- [x] 1.1 `LEARNING_APP_PORT` → `LEARNING_APP__PORT` in `src/backend/core.clj` and `test/browser/README.md`
- [x] 1.2 `LEARNING_APP_DB_BACKUP_PATH` → `LEARNING_APP__DB_BACKUP_PATH` in the backup unit `Environment=` and `backup.sh`
- [x] 1.3 `LEARNING_APP_DB_PATH` → `LEARNING_APP__DB_PATH` in `backup.sh` (default, export, sqlite3 use)

## 2. Verification

- [x] 2.1 `grep -rn "LEARNING_APP_[A-Z]"` over src, infra, docs, test — only the AUTH_SECRET pair (phase 2) remains
- [x] 2.2 Backend tests stay green (22 tests / 49 assertions)
- [x] 2.3 `LEARNING_APP__PORT=9999` → `core/port` answers 9999; legacy name ignored
- [x] 2.4 `bash -n backup.sh`; `systemd-analyze verify` on the backup unit
