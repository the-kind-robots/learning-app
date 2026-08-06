# production-service-hardening Specification

## Purpose
TBD - created by archiving change harden-systemd-services. Update Purpose after archive.
## Requirements
### Requirement: Service units are sandboxed to their observed needs
Every learning-app unit with a `[Service]` section SHALL declare a systemd hardening block that leaves writable only the paths the service demonstrably writes, reachable only the address families it demonstrably uses, and grants no capabilities beyond what its user and task require. Directives whose compatibility with the workload cannot be established SHALL be omitted and the omission recorded, never guessed at.

#### Scenario: App service keeps serving
- **WHEN** learning-app-run starts under the hardening block
- **THEN** it binds its port, reaches CouchDB on localhost and OpenRouter over HTTPS, writes its SQLite files, and reads its decrypted credentials

#### Scenario: JIT and FFI workloads survive
- **WHEN** a unit runs a JVM or Python-with-cffi workload
- **THEN** MemoryDenyWriteExecute is off for that unit, with the reason recorded in the unit file

#### Scenario: Directives actually parse
- **WHEN** `systemd-analyze verify` runs against the units
- **THEN** no directive is ignored as unparseable — in particular, no inline comments on directive lines

### Requirement: Each shared systemd resource directory has exactly one owning unit

A directory managed through `StateDirectory=` or `LogsDirectory=` SHALL be declared by exactly one unit — the one whose user owns it. Any other unit needing access SHALL get it through `ReadWritePaths=` plus group membership, never by declaring the same directory, so that no service start rechowns a tree another service depends on.

#### Scenario: Backup runs, then the app restarts

- **WHEN** learning-app-backup-db runs and learning-app-run restarts afterwards, in either order
- **THEN** `/var/lib/learning-app` stays owned by webapp:webapp throughout, and the backup reads and snapshots the database via group permissions

#### Scenario: The database is quiescent when the backup fires

- **WHEN** learning-app-backup-db runs while learning-app-run is stopped and no journal/-wal/-shm files exist
- **THEN** sqlite3 can create them in the setgid group-writable state directory and the backup completes

