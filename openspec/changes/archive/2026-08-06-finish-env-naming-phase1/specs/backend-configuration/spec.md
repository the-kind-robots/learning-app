## ADDED Requirements

### Requirement: Environment variables follow the LEARNING_APP__ prefix convention
Every application environment variable SHALL be named `LEARNING_APP__XXX` — double underscore between the app prefix and the variable name. Legacy single-underscore names SHALL NOT be read; a rename that spans more than one deploy artifact SHALL ship as a dual-read transition before the old name is dropped.

#### Scenario: Double-underscore name is honored
- **WHEN** the backend starts with `LEARNING_APP__PORT=9999`
- **THEN** it listens on 9999

#### Scenario: Legacy single-underscore name is ignored
- **WHEN** the backend starts with only `LEARNING_APP_PORT=9999` set
- **THEN** it listens on the default port 8083

#### Scenario: Backup pipeline uses the convention
- **WHEN** the backup unit runs `backup.sh`
- **THEN** the database and backup paths flow through `LEARNING_APP__DB_PATH` and `LEARNING_APP__DB_BACKUP_PATH`, both wired within the same deb artifact
