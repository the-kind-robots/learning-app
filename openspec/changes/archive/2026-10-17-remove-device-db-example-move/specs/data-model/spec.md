## MODIFIED Requirements

### Requirement: The app keeps no index
The app SHALL create no index in user-db or in device-db, and SHALL bring none up to date; so the splash waits for none. An index an earlier build left in user-db or in device-db, such as the one its task queue kept, SHALL be neither queried nor brought up to date. A start SHALL NOT move examples out of device-db, delete task documents or indexes from it, or otherwise tidy it.

#### Scenario: A new installation
- **WHEN** the app starts on a device with empty databases and memory is loaded
- **THEN** `getIndexes()` on user-db lists `_all_docs` only
- **AND** `getIndexes()` on device-db lists `_all_docs` only

#### Scenario: An index an earlier build left in user-db
- **WHEN** the app starts on a device whose user-db holds `_design/by-type` from an earlier build
- **THEN** the app neither queries that index nor brings it up to date

#### Scenario: A start leaves device-db as it is
- **WHEN** the app starts on a device whose `device-db` holds example or task documents from an earlier build
- **THEN** no example is written to `user-db` for them and no document is deleted from `device-db`
- **AND** the screen asked for is shown once memory is loaded

## REMOVED Requirements

### Requirement: Examples kept on the device move to user-db
**Reason**: Every device in use has run the move and the sweep (#541); a start no longer moves examples out of `device-db` or tidies it.
**Migration**: None. `device-db` keeps the device identity and the migration records; any example or task document left in it is ignored.
