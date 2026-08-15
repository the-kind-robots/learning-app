## ADDED Requirements

### Requirement: A migration reads every document it claims to migrate

A migration that copies or rewrites stored documents SHALL read every document matching
its query, not the first page of them. Queries backing a migration SHALL NOT rely on the
storage layer's default result limit.

#### Scenario: A type with more documents than the default page size

- **WHEN** the source database holds more documents of a migrated type than the storage
  layer's default query limit
- **THEN** every one of them is copied to the destination database
- **AND** the count in the destination equals the count in the source

#### Scenario: An empty source database

- **WHEN** a migration runs against a source database that holds no documents
- **THEN** it completes without error and copies nothing

### Requirement: An incomplete migration is repaired, not skipped

A migration whose recorded run copied only part of its documents SHALL be repaired on a
later boot. The marker of the original run SHALL be left in place, and the repair SHALL
record its own marker so that it runs once per browser.

#### Scenario: A browser that recorded a truncated run

- **WHEN** the app boots on a browser whose migration marker is present but whose source
  database still holds documents that were never copied
- **THEN** the remaining documents are copied to their destination databases
- **AND** the repair records its own marker

#### Scenario: A browser that migrated completely

- **WHEN** the app boots on a browser whose migration copied every document
- **THEN** the repair changes no document
- **AND** it records its marker so it does not run again

### Requirement: A migration never resurrects a deleted document

A migration SHALL NOT recreate a document that the destination database has already seen,
including one the user has since deleted. A document SHALL be copied only when its id is
unknown to the destination.

#### Scenario: A word deleted after an incomplete migration

- **WHEN** a word was copied by a truncated migration and the user deleted it afterwards
- **AND** the repair runs
- **THEN** the word stays deleted

#### Scenario: A document edited after it was copied

- **WHEN** a copied document was modified in the destination database
- **AND** the repair runs
- **THEN** the destination keeps its own version
