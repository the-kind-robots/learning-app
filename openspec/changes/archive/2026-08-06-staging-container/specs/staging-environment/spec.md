# staging-environment Specification

## ADDED Requirements

### Requirement: The packaged shape is verifiable without a server
A staging run SHALL build the infra deb exactly as CI builds it, install it inside a systemd container, and verify the resulting shape by script: services under their production sandboxes, nginx answering, the authentication boundary, migrations against the configured database path, legacy database adoption, and a backup archive carrying both stores. Each assertion SHALL report PASS or FAIL on its own line and the run SHALL exit nonzero when any assertion fails.

#### Scenario: An infra change before merge
- **WHEN** `infra/staging/run.sh` runs against the working tree
- **THEN** the actual deb installs in a booted systemd container and every smoke assertion reports PASS or FAIL, with a nonzero exit on any FAIL

#### Scenario: A deliberately broken unit
- **WHEN** a sandbox directive the service depends on is removed (e.g. a ReadWritePaths)
- **THEN** the run reports the failure instead of green

### Requirement: The container rung stays honest about its limits
The staging container SHALL NOT contact production systems or real credential stores: certbot never issues, borg targets a local repository, all secrets are test-only values, and the kernel is the host's. Claims beyond the shape — real TLS, remote backup, multi-day behavior — SHALL remain with the metal rung (ADR-0010).

#### Scenario: Reading a staging run's result
- **WHEN** the smoke passes
- **THEN** what is proven is the packaged shape — install, boot, sandboxes, data flow — and nothing about production reality
