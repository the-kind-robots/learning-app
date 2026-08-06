# backup-pipeline Specification

## Purpose
TBD - created by archiving change couchdb-in-backups. Update Purpose after archive.
## Requirements
### Requirement: Both stores are backed up as one coherent archive
Every backup run SHALL capture the SQLite database and the CouchDB data tree in a single archive, taken back to back, so a restore recovers matching account rows and userdbs. Backup SHALL read CouchDB with access scoped to the backup service.

#### Scenario: A backup run
- **WHEN** the backup timer fires
- **THEN** one archive contains the SQLite snapshot and /var/lib/couchdb

#### Scenario: A restore
- **WHEN** an operator restores from an archive
- **THEN** both stores come from the same moment and an existing token authenticates against its userdb

