## MODIFIED Requirements

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
