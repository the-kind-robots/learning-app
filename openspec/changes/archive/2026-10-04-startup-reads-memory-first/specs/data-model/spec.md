## ADDED Requirements

### Requirement: The splash waits for no index
The app SHALL create no index in user-db and SHALL bring none up to date there. It SHALL create no secondary index and bring none up to date before memory is loaded (`specs/learner-data-memory/spec.md`). device-db SHALL carry the index the task queue selects its due tasks by; the task queue SHALL create it and bring it up to date when it starts, after memory is loaded.

#### Scenario: A new installation
- **WHEN** the app starts on a device with empty databases and memory is loaded
- **THEN** `getIndexes()` on user-db lists `_all_docs` only

#### Scenario: An index an earlier build left in user-db
- **WHEN** the app starts on a device whose user-db holds `_design/by-type` from an earlier build
- **THEN** the app neither queries that index nor brings it up to date

#### Scenario: Tasks rewritten since the last start
- **WHEN** the task queue rewrote its tasks before the app was closed, and the app starts again
- **THEN** memory is loaded before the task queue's index is brought up to date

## REMOVED Requirements

### Requirement: user-db carries secondary indexes
**Reason**: Nothing queries user-db by an index. Bringing the `type` index up to date on every start put a scan of everything written since the last start in front of the splash.
**Migration**: None. A device that has `_design/by-type` keeps it; the app neither queries it nor brings it up to date.
