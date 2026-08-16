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
