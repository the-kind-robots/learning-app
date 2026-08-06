# production-service-hardening Specification

## ADDED Requirements

### Requirement: Each shared systemd resource directory has exactly one owning unit

A directory managed through `StateDirectory=` or `LogsDirectory=` SHALL be declared by exactly one unit — the one whose user owns it. Any other unit needing access SHALL get it through `ReadWritePaths=` plus group membership, never by declaring the same directory, so that no service start rechowns a tree another service depends on.

#### Scenario: Backup runs, then the app restarts

- **WHEN** learning-app-backup-db runs and learning-app-run restarts afterwards, in either order
- **THEN** `/var/lib/learning-app` stays owned by webapp:webapp throughout, and the backup reads and snapshots the database via group permissions

#### Scenario: The database is quiescent when the backup fires

- **WHEN** learning-app-backup-db runs while learning-app-run is stopped and no journal/-wal/-shm files exist
- **THEN** sqlite3 can create them in the setgid group-writable state directory and the backup completes
