## MODIFIED Requirements

### Requirement: The packaged shape is verifiable without a server
A staging run SHALL build the infra deb exactly as CI builds it, install it inside a systemd container, and verify the resulting shape by script: services under their production sandboxes, nginx answering, the authentication boundary — including that a client-supplied CouchDB proxy-auth header does not reach an upstream — migrations against the configured database path, legacy database adoption, and a backup archive carrying both stores. Each assertion SHALL report PASS or FAIL on its own line and the run SHALL exit nonzero when any assertion fails.

#### Scenario: An infra change before merge
- **WHEN** `infra/staging/run.sh` runs against the working tree
- **THEN** the actual deb installs in a booted systemd container and every smoke assertion reports PASS or FAIL, with a nonzero exit on any FAIL

#### Scenario: A deliberately broken unit
- **WHEN** a sandbox directive the service depends on is removed (e.g. a ReadWritePaths)
- **THEN** the run reports the failure instead of green

#### Scenario: A spoofed proxy-auth header
- **WHEN** the smoke sends a request through nginx carrying `X-Auth-CouchDB-UserName` while a listener stands in for the upstream
- **THEN** the captured request carries no `X-Auth-CouchDB-*` header, and every proxying location in the installed config is confirmed to clear or assign all three
