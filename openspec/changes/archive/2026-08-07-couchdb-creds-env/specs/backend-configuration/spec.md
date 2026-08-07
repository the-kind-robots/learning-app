## ADDED Requirements

### Requirement: CouchDB credentials come from the environment
The backend SHALL authenticate its server-side CouchDB calls with credentials from `LEARNING_APP__COUCHDB_USER` / `LEARNING_APP__COUCHDB_PASSWORD`, SHALL fall back to the dev-stand values only in a source checkout, and a packaged app SHALL refuse to start without the password rather than run with credentials that 401 on every admin call.

#### Scenario: Packaged app without the password
- **WHEN** the jar starts with no `LEARNING_APP__COUCHDB_PASSWORD` in its environment
- **THEN** boot fails with an error naming the variable

#### Scenario: Source checkout
- **WHEN** the app runs from a checkout with no CouchDB variables set
- **THEN** it uses the dev stand's admin credentials

#### Scenario: Production unit
- **WHEN** the packaged app starts under systemd
- **THEN** the unit exports the decrypted `couchdb_admin_password` credential as `LEARNING_APP__COUCHDB_PASSWORD`
