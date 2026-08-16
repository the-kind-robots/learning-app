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

### Requirement: CouchDB admin provisioning needs no prior password and never fails silently
postinst SHALL align the running CouchDB admin password with the encrypted credential without knowing any previous password: when the credential does not authenticate, it SHALL write the plaintext into a dedicated `local.d` ini — owned by the CouchDB user, not world-readable — and restart CouchDB, which replaces the plaintext with a hash on startup. When the credential already authenticates, postinst SHALL neither write the ini nor restart CouchDB. A credential that still fails after the restart SHALL abort postinst; no step on this path may swallow its failure.

#### Scenario: Credential matches the running instance
- **WHEN** the decrypted credential authenticates against the running CouchDB
- **THEN** postinst writes nothing and does not restart CouchDB

#### Scenario: Credential drifted from the running instance
- **WHEN** the decrypted credential is rejected by the running CouchDB
- **THEN** postinst writes the credential into the dedicated local.d ini and restarts CouchDB
- **AND** afterwards the credential authenticates and the previous password does not

#### Scenario: Plaintext does not persist
- **WHEN** CouchDB has restarted after the ini write
- **THEN** the ini holds a password hash, not the plaintext credential

#### Scenario: Credential still rejected after the restart
- **WHEN** the credential does not authenticate even after the ini write and restart
- **THEN** postinst exits nonzero instead of continuing

### Requirement: Client-supplied CouchDB proxy-auth headers never reach an upstream
nginx SHALL guarantee that no client-supplied value of `X-Auth-CouchDB-UserName`, `X-Auth-CouchDB-Roles` or `X-Auth-CouchDB-Token` reaches an upstream, because CouchDB trusts those headers by construction. Every location that proxies upstream SHALL either clear all three headers or assign all three itself from its `auth_request` variables; a location SHALL NOT rely on inheriting the clearing from `server` scope, because nginx passes `proxy_set_header` down only into locations that declare none of their own. The clearing SHALL live in a single included snippet rather than being repeated per location, and no location SHALL both include the snippet and assign the headers, which would send each header twice.

#### Scenario: A client spoofs the identity header
- **WHEN** a request carrying `X-Auth-CouchDB-UserName` arrives at any location that proxies upstream
- **THEN** the upstream receives that request without the header

#### Scenario: The authenticated database path
- **WHEN** a request with a valid session reaches `/db/`
- **THEN** the three headers arriving upstream are the ones derived from the `auth_request` response, each present exactly once

#### Scenario: A location is added later
- **WHEN** a new proxying location is added to either nginx config
- **THEN** the staging smoke fails unless that location clears the headers or assigns them from `auth_request`

