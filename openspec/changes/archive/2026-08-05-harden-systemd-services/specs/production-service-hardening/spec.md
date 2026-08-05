## ADDED Requirements

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
