# backend-configuration Specification

## Purpose
TBD - created by archiving change configurable-db-path. Update Purpose after archive.
## Requirements
### Requirement: Database path is configurable via environment
The backend SHALL resolve its SQLite database path from `LEARNING_APP__DB_PATH`, defaulting to `app.db` relative to the working directory when the variable is unset. The resolved spec SHALL live in exactly one place, used by the server, handlers, and migrations alike.

#### Scenario: Env var points elsewhere
- **WHEN** the backend starts with `LEARNING_APP__DB_PATH=/some/path/app.db`
- **THEN** migrations and all queries run against `/some/path/app.db`

#### Scenario: Env var unset
- **WHEN** the backend starts without `LEARNING_APP__DB_PATH`
- **THEN** it uses `app.db` in the working directory, identical to prior behavior

### Requirement: A legacy database is adopted, never stranded or clobbered
On startup, before opening the database, the backend SHALL move a database found at the legacy location into the configured path when the target does not exist, crash-safely. When both exist it SHALL prefer the target, leave the legacy file in place and log loudly. The move and the path-reading SHALL ship in one artifact.

#### Scenario: First boot after the path change
- **WHEN** the configured path is empty and the legacy file exists
- **THEN** the data moves, the event is logged, and existing accounts keep working

#### Scenario: Rollback then roll forward
- **WHEN** both the legacy and the target exist
- **THEN** the target wins, nothing is deleted, and the log names both paths for the operator

### Requirement: Environment variables follow the LEARNING_APP__ prefix convention
Every application environment variable SHALL be named `LEARNING_APP__XXX` — double underscore between the app prefix and the variable name. Legacy single-underscore names SHALL NOT be read, with one transitional exception: a rename that spans more than one deploy artifact SHALL ship as a dual-read transition — the reader prefers the new name and falls back to the old — before the old name is dropped. During the `LEARNING_APP_DB_AUTH_SECRET` transition (#217 phase 2a) the jar SHALL prefer `LEARNING_APP__DB_AUTH_SECRET`, fall back to the old name, and name the new variable when refusing to boot; the fallback SHALL be removed (phase 2c) once the unit's rename (phase 2b) is deployed.

#### Scenario: Double-underscore name is honored
- **WHEN** the backend starts with `LEARNING_APP__PORT=9999`
- **THEN** it listens on 9999

#### Scenario: Legacy single-underscore name is ignored
- **WHEN** the backend starts with only `LEARNING_APP_PORT=9999` set
- **THEN** it listens on the default port 8083

#### Scenario: Backup pipeline uses the convention
- **WHEN** the backup unit runs `backup.sh`
- **THEN** the database and backup paths flow through `LEARNING_APP__DB_PATH` and `LEARNING_APP__DB_BACKUP_PATH`, both wired within the same deb artifact

#### Scenario: Old unit, new jar — the secret still resolves
- **WHEN** only `LEARNING_APP_DB_AUTH_SECRET` is set, as the pre-rename unit exports it
- **THEN** the jar signs with that value

#### Scenario: Both secret names set
- **WHEN** both `LEARNING_APP__DB_AUTH_SECRET` and `LEARNING_APP_DB_AUTH_SECRET` are set
- **THEN** the new name wins

#### Scenario: Packaged app with neither secret name
- **WHEN** neither name is set outside a source checkout
- **THEN** boot is refused with an error naming `LEARNING_APP__DB_AUTH_SECRET`

### Requirement: Git dependency coordinates are internally consistent
Every `:git/tag` + `:git/sha` coordinate in the repository SHALL name the same commit: the sha SHALL be a prefix of the commit the tag points to. A coordinate SHALL be proven by resolving it with cold dependency caches, not only on machines whose caches already hold the commit.

#### Scenario: A pinned git dependency is bumped
- **WHEN** a `:git/tag` is changed in any `deps.edn`
- **THEN** the paired `:git/sha` is updated to the commit that tag dereferences to, and the build resolves from a clean gitlibs and maven cache

#### Scenario: Tag and sha disagree
- **WHEN** a coordinate pins a tag with the sha of a different commit
- **THEN** a clean-cache build fails, and the fix is aligning the sha with the tag

### Requirement: CouchDB credentials come from the environment
The backend SHALL authenticate its server-side CouchDB calls with credentials from `LEARNING_APP__COUCHDB_USER` / `LEARNING_APP__COUCHDB_PASSWORD`, SHALL fall back to the dev-stand values only in a source checkout, and a packaged app SHALL refuse to start without the password rather than run with credentials that 401 on every admin call.

#### Scenario: Packaged app without the password
- **WHEN** the jar starts with no `LEARNING_APP__COUCHDB_PASSWORD` in its environment
- **THEN** boot fails with an error naming the variable

#### Scenario: Source checkout
- **WHEN** the app runs from a checkout with no CouchDB variables set
- **THEN** it uses the dev stand's admin credentials

#### Scenario: Production unit
- **WHEN** the packaged app starts under systemd
- **THEN** the unit exports the decrypted `couchdb_admin_password` credential as `LEARNING_APP__COUCHDB_PASSWORD`

### Requirement: Dev builds ship distinct PWA identity assets

The HTML shell SHALL reference a dev manifest whose icons carry a red letter D badge when the backend runs from source, and the unbadged production manifest when running packaged, so dev and production installs are visually distinct on one device.

#### Scenario: Running from source

- **WHEN** the backend serves the HTML shell while running from a checkout
- **THEN** the manifest link points to the dev manifest
- **AND** the dev manifest lists the badged icon variants

#### Scenario: Running packaged

- **WHEN** the backend serves the HTML shell from the packaged jar
- **THEN** the manifest link points to the production manifest with the unbadged icons

