## MODIFIED Requirements

### Requirement: user-db carries secondary indexes
user-db SHALL carry a secondary index on `type`. The system SHALL create it when the databases are initialised at start-up, so an installation that predates it gets it on its next start without a migration.

#### Scenario: Indexes exist after start-up
- **WHEN** the app has started and the databases are initialised
- **THEN** `getIndexes()` on user-db lists an index whose fields are `["type"]`, alongside `_all_docs`

#### Scenario: An existing installation gets the indexes
- **WHEN** an installation whose user-db has only `_all_docs` starts
- **THEN** after start-up `getIndexes()` on user-db lists the `type` index

## REMOVED Requirements

### Requirement: user-db carries a reviews view
**Reason**: Retention is computed from the reviews held in memory (learner-data-memory); no code queries the view.
**Migration**: None. A device that has the design document keeps it; nothing queries it.
