## ADDED Requirements

### Requirement: Export covers every synced document
The system SHALL export a versioned payload containing every document in the local `user-db`, regardless of type, so a new document type can never be silently dropped.

#### Scenario: Backup includes collections
- **WHEN** an export is produced on a device that has vocab, reviews, and collections
- **THEN** the payload contains all of them

#### Scenario: New doc type survives export
- **WHEN** a future feature adds a new synced document type
- **THEN** exports include it with no export-code change

#### Scenario: Versioned payload
- **WHEN** an export payload is produced
- **THEN** it carries a schema version

### Requirement: Import merges by document type
The system SHALL import a payload by dispatching on each document's `:type`: vocab merges last-write-wins by `:modified-at`; any other type inserts if absent (idempotent union).

#### Scenario: Re-import is idempotent
- **WHEN** the same payload is imported twice
- **THEN** the second import changes nothing

#### Scenario: Unknown type still imports
- **WHEN** a payload contains a document type the importer has no special handling for
- **THEN** the document is inserted if absent rather than dropped

### Requirement: Provisioned account is seeded by first-sync push
The system SHALL seed a newly provisioned `userdb` by the device pushing its local `user-db` over normal replication; provisioning SHALL NOT upload a payload.

#### Scenario: Seed on provision
- **WHEN** an account is provisioned from a device holding local data
- **THEN** the device's next sync pass pushes that data into the fresh `userdb`

#### Scenario: Joining device sees the data
- **WHEN** a second device enters the account key after the seed push
- **THEN** replication delivers the first device's vocab, reviews, and collections

### Requirement: Key share is client-side
The system SHALL compose any share or email of the account key on the client, and the server SHALL NOT receive or store user email addresses for this purpose.

#### Scenario: Client-composed share
- **WHEN** a user shares their account key
- **THEN** the link is composed by the client via share, copy, or mailto
- **AND** the server does not receive the address
