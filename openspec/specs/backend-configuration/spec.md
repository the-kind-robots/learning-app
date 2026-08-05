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

