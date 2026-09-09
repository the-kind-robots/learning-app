## MODIFIED Requirements

### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations and ISO 8601 timestamps for creation and modification. A translation SHALL be stored as it was entered: the entered text becomes one `translation` entry and SHALL NOT be split on punctuation. `translation` stays a vector, so a document written before this rule keeps its several entries and is read as it is.

#### Scenario: Vocabulary document shape
- **WHEN** a vocabulary word is stored
- **THEN** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`

Example:
```json
{
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

#### Scenario: A translation spanning several lines stays whole
- **WHEN** a word is added with a translation typed over several lines
- **THEN** its `translation` holds one entry carrying the line breaks as typed

#### Scenario: An edited translation follows the same rule
- **WHEN** an existing word document's translation is replaced with `без того, чтобы`
- **THEN** its `translation` holds one entry whose value is `без того, чтобы`

#### Scenario: A blank translation stores nothing
- **WHEN** a word is submitted with a translation that is empty or only whitespace
- **THEN** no translation entry is produced and the add is rejected as empty

#### Scenario: Documents written before the rule are read unchanged
- **WHEN** a stored word document holds several `translation` entries
- **THEN** it is read as it is, with no migration and no merging of its entries
