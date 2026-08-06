# The database moves itself to its configured home

## Why

Production's EnvironmentFile has pointed at `/var/lib/learning-app/db.sqlite` all along while the app wrote `/opt/learning-app/app.db` — so backups archived a file nobody writes (#201), and merging the env-honoring change alone (#196) would have stranded every account on an empty database. Moving the file in the deploy pipeline and teaching the app the path are two changes; any deploy carrying only one of them causes the outage.

## What changes

The backend adopts the legacy database itself at startup, in the same artifact that reads `LEARNING_APP__DB_PATH`: copy under a temp name, atomic rename, delete the legacy trio — crash-safe, and an existing target is never clobbered (rollback must not cost newer data). Infra needs nothing: `StateDirectory` already creates and unlocks the directory, environment.d already carries the path, backup.sh already points at it.

## Impact

Backend only (`db-spec` from env — absorbed from #196 — plus adoption); runbook note; four tests; end-to-end simulation. Supersedes PR #196.
