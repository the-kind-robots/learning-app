## ADDED Requirements

### Requirement: A recycled account id never inherits an existing userdb
Provisioning SHALL refuse to create an account whose derived userdb already exists in CouchDB, leaving the user row unwritten and the stale userdb untouched. The refusal SHALL be observable to the operator.

#### Scenario: Stale userdb from a previous owner
- **WHEN** provisioning would assign an id whose `userdb-<id>` already exists
- **THEN** the request fails with 503, no user row is created, and the event is logged

#### Scenario: Clean id
- **WHEN** no userdb exists for the assigned id
- **THEN** the account is created and its userdb is secured with the account's role
