## ADDED Requirements

### Requirement: user-db carries secondary indexes
user-db SHALL carry a secondary index on `type` and a secondary index on `type` + `word_id`. The system SHALL create both when the databases are initialised at start-up, so an installation that predates them gets them on its next start without a migration.

#### Scenario: Indexes exist after start-up
- **WHEN** the app has started and the databases are initialised
- **THEN** `getIndexes()` on user-db lists an index whose fields are `["type"]` and an index whose fields are `["type", "word_id"]`, alongside `_all_docs`

#### Scenario: An existing installation gets the indexes
- **WHEN** an installation whose user-db has only `_all_docs` starts
- **THEN** after start-up `getIndexes()` on user-db lists both indexes

### Requirement: user-db carries a reviews view
user-db SHALL carry a design document `_design/reviews-by-word` whose view emits, for every `review` document, the key `word_id` and the value `[created_at, retained]`. The system SHALL create it at start-up and replace it when its map function differs from the stored one. Retention levels for a list of words SHALL be computed from this view's rows, not from review documents.

#### Scenario: The view answers per word
- **WHEN** user-db holds reviews for a word
- **THEN** querying `reviews-by-word/reviews` with that word id as key returns one row per review whose value is `[created_at, retained]`

### Requirement: user-db design documents are not replicated
user-db design documents SHALL NOT replicate: a sync pass SHALL carry user documents in both directions and no document whose id begins with `_design/` in either direction.

#### Scenario: A sync pass leaves design documents behind
- **WHEN** a sync pass runs against the account's copy on the server
- **THEN** the remote copy holds no `_design/` documents
- **AND** every user document written on either side is present on the other
