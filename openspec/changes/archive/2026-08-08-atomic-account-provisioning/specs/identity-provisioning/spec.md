## MODIFIED Requirements

### Requirement: A recycled account id never inherits an existing userdb
Provisioning SHALL refuse to create an account whose derived userdb already exists in CouchDB, leaving the user row unwritten and the stale userdb untouched. The refusal SHALL be observable to the operator.

Provisioning SHALL be atomic across the two stores: the user row commits only once its userdb is secured, so a failed CouchDB step leaves no account row behind, and a userdb created by the failed attempt is removed with it (best effort). The id a failed attempt minted is minted again by the next attempt, not leaked.

#### Scenario: Stale userdb from a previous owner
- **WHEN** provisioning would assign an id whose `userdb-<id>` already exists
- **THEN** the request fails with 503, no user row is created, and the event is logged

#### Scenario: Clean id
- **WHEN** no userdb exists for the assigned id
- **THEN** the account is created and its userdb is secured with the account's role

#### Scenario: CouchDB step fails mid-provision
- **WHEN** creating or securing the userdb fails after the row was inserted
- **THEN** the insert rolls back, the userdb the attempt created is removed, and the next attempt provisions the same id cleanly
