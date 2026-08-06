## ADDED Requirements

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
