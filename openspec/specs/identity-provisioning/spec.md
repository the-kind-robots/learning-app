# identity-provisioning Specification

## Purpose
TBD - created by archiving change recycled-id-guard. Update Purpose after archive.
## Requirements
### Requirement: A recycled account id never inherits an existing userdb
Provisioning SHALL refuse to create an account whose derived userdb already exists in CouchDB, leaving the user row unwritten and the stale userdb untouched. The refusal SHALL be observable to the operator.

#### Scenario: Stale userdb from a previous owner
- **WHEN** provisioning would assign an id whose `userdb-<id>` already exists
- **THEN** the request fails with 503, no user row is created, and the event is logged

#### Scenario: Clean id
- **WHEN** no userdb exists for the assigned id
- **THEN** the account is created and its userdb is secured with the account's role

### Requirement: Orphan userdbs are reported, never auto-deleted
The system SHALL report every CouchDB `userdb-N` whose account row `N` is missing from SQLite — at startup and on operator demand — and SHALL NOT delete or tombstone any userdb on its own. A restored-from-older-backup SQLite makes such userdbs live and legitimate, so disposal stays a human decision. Account rows without a userdb are a different signal and are not part of this report.

#### Scenario: Staged orphan userdb
- **WHEN** CouchDB holds `userdb-7` and no `users` row with id 7 exists
- **THEN** the report names `userdb-7` and nothing else, and no data is touched

#### Scenario: Stores agree
- **WHEN** every `userdb-N` has a matching `users` row
- **THEN** the report is empty

#### Scenario: CouchDB unreachable at startup
- **WHEN** the startup report cannot enumerate CouchDB databases
- **THEN** a warning is logged and boot continues

### Requirement: An operator mints an invite from the packaged app

The packaged application SHALL expose a `mint-invite` command-line entry point that stores a single-use grant in the configured database, prints the resulting invite URL, and exits without starting the server. The URL SHALL point at the configured public origin and carry the token in the URL fragment, keeping it out of access logs and Referer headers.

#### Scenario: Minting from the jar

- **WHEN** the operator runs the jar with the `mint-invite` argument
- **THEN** database adoption and migrations run first, a single-use grant is stored, the invite URL is printed, and no server starts

#### Scenario: Configured public origin

- **WHEN** `LEARNING_APP__PUBLIC_URL` is set
- **THEN** the printed URL uses that origin, with no doubled slash before the fragment

#### Scenario: Unknown command

- **WHEN** the entry point receives an argument that is not a known command
- **THEN** the process names the unknown command on stderr and exits nonzero

