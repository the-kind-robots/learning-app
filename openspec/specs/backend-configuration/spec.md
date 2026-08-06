# backend-configuration Specification

## Purpose
TBD - created by archiving change configurable-db-path. Update Purpose after archive.
## Requirements
### Requirement: Database path is configurable via environment
The backend SHALL resolve its SQLite database path from `LEARNING_APP_DB_PATH`, defaulting to `app.db` relative to the working directory when the variable is unset. The resolved spec SHALL live in exactly one place, used by the server, handlers, and migrations alike.

#### Scenario: Env var points elsewhere
- **WHEN** the backend starts with `LEARNING_APP_DB_PATH=/some/path/app.db`
- **THEN** migrations and all queries run against `/some/path/app.db`

#### Scenario: Env var unset
- **WHEN** the backend starts without `LEARNING_APP_DB_PATH`
- **THEN** it uses `app.db` in the working directory, identical to prior behavior

### Requirement: A legacy database is adopted, never stranded or clobbered
On startup, before opening the database, the backend SHALL move a database found at the legacy location into the configured path when the target does not exist, crash-safely. When both exist it SHALL prefer the target, leave the legacy file in place and log loudly. The move and the path-reading SHALL ship in one artifact.

#### Scenario: First boot after the path change
- **WHEN** the configured path is empty and the legacy file exists
- **THEN** the data moves, the event is logged, and existing accounts keep working

#### Scenario: Rollback then roll forward
- **WHEN** both the legacy and the target exist
- **THEN** the target wins, nothing is deleted, and the log names both paths for the operator

