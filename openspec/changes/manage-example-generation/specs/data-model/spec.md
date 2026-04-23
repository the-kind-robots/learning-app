## MODIFIED Requirements

### Requirement: Example documents are stored
The system SHALL store example documents linked to vocabulary words with source, preference, structure status, and ISO 8601 timestamps.

#### Scenario: Example document shape
- **WHEN** an example is stored
- **THEN** the document includes `type`, `word-id`, `word`, `value`, `translation`, `source`, `preferred?`, `structure-status`, `created-at`, and `modified-at`
- **AND** the document includes `structure` when sentence structure is ready

Example:
```json
{
  "type": "example",
  "word-id": "<vocab-id>",
  "word": "der Hund",
  "value": "Der Hund schlaeft unter dem Tisch.",
  "translation": "The dog sleeps under the table.",
  "source": "ai",
  "preferred?": true,
  "structure-status": "ready",
  "structure": [
    {"usedForm": "Hund", "dictionaryForm": "der Hund", "translation": "dog", "wordIndex": 1}
  ],
  "created-at": "2026-01-20T10:02:00.000Z",
  "modified-at": "2026-01-20T10:02:00.000Z"
}
```

#### Scenario: Unstructured user example shape
- **WHEN** a user-owned example is stored
- **THEN** the document includes `source` set to `"user"`
- **AND** the document includes `structure-status` set to `"absent"`
- **AND** the document MAY omit `structure`

### Requirement: Task documents are stored
The system SHALL store task documents for asynchronous work with ISO 8601 scheduling and creation timestamps.

#### Scenario: Task document shape
- **WHEN** a task is stored
- **THEN** it includes `type`, `task-type`, `data`, `attempts`, `run-at`, and `created-at`
- **AND** unique tasks use deterministic short `_id` values derived from task type and normalized data

Example:
```json
{
  "_id": "task:example-fetch:<8-hex-data-hash>",
  "type": "task",
  "task-type": "example-fetch",
  "data": {"word-id": "<vocab-id>"},
  "attempts": 0,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z"
}
```

### Requirement: Dead-lettered tasks are recorded
The system SHALL record failed tasks as dead-lettered task documents with an ISO 8601 failure timestamp and the latest available failure detail.

#### Scenario: Dead-letter task shape
- **WHEN** a task is dead-lettered
- **THEN** it includes `status`, `failure-reason`, and `failed-at`
- **AND** it includes `last-error` when failure detail is available

Example:
```json
{
  "_id": "task:example-fetch:<8-hex-data-hash>",
  "type": "task",
  "task-type": "example-fetch",
  "data": {"word-id": "<vocab-id>"},
  "attempts": 5,
  "run-at": null,
  "created-at": "2026-01-20T10:00:00.000Z",
  "status": "failed",
  "failure-reason": "retry-limit-reached",
  "last-error": "Server error fetching example",
  "failed-at": "2026-01-20T10:06:00.000Z"
}
```
