# collections-data-model Specification

## Purpose
Define PouchDB document shapes for collections and word-collection membership, plus the active-collection persistence contract.

## Requirements

### Requirement: Collection documents are stored
The system SHALL store collection documents in `device-db` with a unique name and ISO 8601 creation timestamp.

#### Scenario: Collection document shape
- **WHEN** a collection is created
- **THEN** a document is stored with `type: "collection"`, `name`, and `created-at`

Example:
```json
{
  "_id": "collection-abc123",
  "type": "collection",
  "name": "Driving School",
  "created-at": "2026-05-22T10:00:00.000Z"
}
```

### Requirement: All Words is a virtual undeletable default collection
The system SHALL treat the constant `"all-words"` as the default collection ID. No document is stored for All Words. All Words SHALL NOT be deletable or renameable.

#### Scenario: All Words always present in collection list
- **WHEN** the collection list is requested
- **THEN** All Words appears as the first entry regardless of stored collection documents

#### Scenario: All Words cannot be deleted
- **WHEN** delete is requested for collection-id "all-words"
- **THEN** the system rejects the request and takes no action

### Requirement: Word-collection membership documents are stored
The system SHALL store word-collection membership documents in `device-db` when a word is added to a named collection (not All Words, which is virtual).

#### Scenario: Word-collection membership document shape
- **WHEN** a word is associated with a named collection
- **THEN** a document is stored with `type: "word-collection"`, `word-id`, and `collection-id`

Example:
```json
{
  "_id": "wc-wordid-collectionid",
  "type": "word-collection",
  "word-id": "<vocab-id>",
  "collection-id": "collection-abc123"
}
```

### Requirement: Active collection persists across restarts
The system SHALL persist the active collection ID in `localStorage` under the key `"active-collection-id"`. On load, if the stored ID is missing or refers to a deleted collection, the system SHALL default to `"all-words"`.

#### Scenario: Active collection restored on app load
- **WHEN** the app starts and localStorage contains a valid `"active-collection-id"`
- **THEN** the active collection is set to that collection

#### Scenario: Active collection defaults when stored ID is invalid
- **WHEN** the app starts and localStorage contains a collection-id that no longer exists
- **THEN** the active collection defaults to "all-words"

### Requirement: Word addition attaches to active collection
The system SHALL attach newly added words to the active collection (if the active collection is not All Words) by creating a word-collection membership document.

#### Scenario: Adding word under named collection creates membership
- **WHEN** a word is added while the active collection is a named collection
- **THEN** a word-collection membership document is created linking the word to that collection

#### Scenario: Adding word under All Words creates no membership doc
- **WHEN** a word is added while the active collection is All Words
- **THEN** no word-collection document is created (All Words is virtual)

### Requirement: Collection deletion removes membership documents
The system SHALL delete all word-collection membership documents for a collection when that collection is deleted. Words are NOT deleted.

#### Scenario: Deleted collection cleans up memberships
- **WHEN** a named collection is deleted
- **THEN** all word-collection docs with that collection-id are deleted
- **AND** the word documents themselves remain untouched
