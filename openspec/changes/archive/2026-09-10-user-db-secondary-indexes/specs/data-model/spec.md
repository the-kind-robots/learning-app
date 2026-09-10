## ADDED Requirements

### Requirement: user-db carries secondary indexes
user-db SHALL carry a secondary index on `type` and a secondary index on `type` + `word_id`. The system SHALL create both when the databases are initialised at start-up, so an installation that predates them gets them on its next start without a migration.

#### Scenario: Indexes exist after start-up
- **WHEN** the app has started and the databases are initialised
- **THEN** `getIndexes()` on user-db lists an index whose fields are `["type"]` and an index whose fields are `["type", "word_id"]`, alongside `_all_docs`

#### Scenario: An existing installation gets the indexes
- **WHEN** an installation whose user-db has only `_all_docs` starts
- **THEN** after start-up `getIndexes()` on user-db lists both indexes
