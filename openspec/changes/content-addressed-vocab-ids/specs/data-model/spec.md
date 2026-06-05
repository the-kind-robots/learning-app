## MODIFIED Requirements

### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations and ISO 8601 timestamps for creation and modification. The document `_id` SHALL be content-addressed as `"vocab:" + normalize(value)`, so the same word maps to the same document id on every device. Because the `_id` is permanent, the `value` normalization is a frozen contract — changing it requires migrating existing vocab ids.

#### Scenario: Vocabulary document shape
- **WHEN** a vocabulary word is stored
- **THEN** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`
- **AND** the document `_id` equals `"vocab:" + normalize(value)`

Example:
```json
{
  "_id": "vocab:hund",
  "type": "vocab",
  "value": "der Hund",
  "translation": [{"lang": "en", "value": "dog"}],
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z"
}
```

#### Scenario: Same word converges across devices
- **WHEN** two devices independently add the same word and then sync
- **THEN** both produce the same `_id`
- **AND** replication yields one document with a revision conflict, not two duplicate documents

## ADDED Requirements

### Requirement: Vocab conflict resolution unions translations
The system SHALL resolve a vocab document revision conflict by unioning the translations of the conflicting revisions, not by plain last-write-wins.

#### Scenario: Concurrent translations merge
- **WHEN** two devices add the same word with different translations and sync
- **THEN** the resolved document contains the union of both translation sets
- **AND** neither side's translation is dropped
