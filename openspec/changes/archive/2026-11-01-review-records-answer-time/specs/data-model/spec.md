## MODIFIED Requirements

### Requirement: Review documents are stored
The system SHALL store review documents linked to vocabulary words with an ISO 8601 creation timestamp. A review MAY carry `answer-ms`, a whole number of milliseconds the learner took to answer. A review without `answer-ms` SHALL be read as one whose time is unknown, so reviews written before the field existed need no migration.

#### Scenario: Review document shape
- **WHEN** a review is recorded
- **THEN** the document includes `type`, `word-id`, `retained`, `created-at`, and `translation`

#### Scenario: A review without a time
- **WHEN** a review document has no `answer-ms`
- **THEN** its time to answer is unknown, and it is read as before otherwise

Example:
```json
{
  "type": "review",
  "word-id": "<vocab-id>",
  "retained": true,
  "created-at": "2026-01-20T10:00:00.000Z",
  "translation": [{"lang": "en", "value": "dog"}],
  "answer-ms": 3420
}
```
