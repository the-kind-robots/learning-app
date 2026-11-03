## MODIFIED Requirements

### Requirement: Collection documents are stored in the user database
The system SHALL store a collection as one document of `type: "collection"` in the user database (`:user/db`), carrying a `name`, an ISO 8601 creation timestamp, an array of word ids and a `position` — a number giving the place of its tile on the themes screen — under a generated document id. A document written before positions existed carries none until its tile is first moved. The repository SHALL hand it outward as `{:id :name :word-ids :created-at :position}`, with `word-ids` an empty vector when the document carries none and `position` absent when it carries none.

#### Scenario: Collection document shape
- **WHEN** a collection is created
- **THEN** a document is stored in the user database with `type: "collection"`, the name, the creation timestamp, an empty word-ids array and a position after every other collection's

Example — the storage layer writes the field names in snake case:
```json
{
  "_id": "6f0b1c8a-1f2d-4a1e-9d3c-0f5b7a2c9e41",
  "type": "collection",
  "name": "Driving School",
  "created_at": "2026-05-22T10:00:00.000Z",
  "position": 3,
  "word_ids": []
}
```

### Requirement: A conflicted collection merges its versions
A collection SHALL carry `renamed-at`, the time it was created or last renamed. When a collection has several live versions, the system SHALL write over the version with the latest `renamed-at` a collection whose word ids are the union of every live version's word ids and whose name and position are that version's, and SHALL remove the other versions. A word removed on one device SHALL return when another device's version still lists it. A change written to a version that stopped being served SHALL be kept by the union.

#### Scenario: Two devices add different words offline
- **WHEN** two devices add different words to one collection before they sync
- **THEN** after both have synced the collection lists both words on both devices
- **AND** it has one live version

#### Scenario: Two devices rename offline
- **WHEN** two devices give one collection different names before they sync
- **THEN** the collection carries the name given last

#### Scenario: A rename and a later add on another device
- **WHEN** one device renames a collection and another device later adds a word to it before they sync
- **THEN** the collection keeps the rename and lists the added word

#### Scenario: A change on the version that lost
- **WHEN** a change was written to a version that another device's version then outranked
- **THEN** the merged collection lists what that change added
