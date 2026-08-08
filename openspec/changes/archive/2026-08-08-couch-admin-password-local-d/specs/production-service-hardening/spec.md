## ADDED Requirements

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
