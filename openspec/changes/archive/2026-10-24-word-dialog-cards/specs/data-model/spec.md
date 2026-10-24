## ADDED Requirements

### Requirement: Vocabulary documents are stored with a translation to a line
The system SHALL store vocabulary documents with translations and ISO 8601 timestamps for creation and modification. A translation SHALL be stored as it was entered: the entered text of a word becomes one `translation` entry for each of its non-blank lines, trimmed, and SHALL NOT be split on punctuation. The text of a phrase becomes one entry. `translation` stays a vector, so a document written before this rule keeps its several entries and is read as it is. The document `_id` SHALL be content-addressed as `"vocab:" + normalize(value)`, so the same word maps to the same document id on every device. Because the `_id` is permanent, the `value` normalization is a frozen contract — changing it requires migrating existing vocab ids.

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

#### Scenario: A translation containing punctuation stays whole
- **WHEN** a word is added with the translation `без того, чтобы`
- **THEN** its `translation` holds one entry whose value is `без того, чтобы`
- **AND** the same holds for a translation containing `;`, `.` or `/`

#### Scenario: Every line is a translation
- **WHEN** a word is added, or edited, with a translation typed over several lines, some blank
- **THEN** its `translation` holds one entry for each non-blank line, trimmed, in order

#### Scenario: An edited translation follows the same rule
- **WHEN** an existing word document's translation is replaced with `без того, чтобы`
- **THEN** its `translation` holds one entry whose value is `без того, чтобы`

#### Scenario: A blank translation stores nothing
- **WHEN** a word is submitted with a translation that is empty or only whitespace
- **THEN** no translation entry is produced and the add is rejected as empty

#### Scenario: Documents written before the rule are read unchanged
- **WHEN** a stored word document holds several `translation` entries
- **THEN** it is read as it is, with no migration and no merging of its entries

#### Scenario: Same word converges across devices
- **WHEN** two devices independently add the same word and then sync
- **THEN** both produce the same `_id`
- **AND** replication yields one document with a revision conflict, not two duplicate documents

## REMOVED Requirements

### Requirement: Vocabulary documents are stored
**Reason**: A translation is stored one to a line.
**Migration**: «Vocabulary documents are stored with a translation to a line».
