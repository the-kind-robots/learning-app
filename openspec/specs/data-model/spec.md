## Purpose
The data model spec defines the required document shapes stored in the local database.
## Requirements
### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations and ISO 8601 timestamps for creation and modification.

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

### Requirement: A vocabulary document carries its kind
The system SHALL store phrases as vocabulary documents: `type` `"vocab"`, the content-addressed `_id` of ADR-0008 (`vocab:` plus the normalized value, spaces preserved), and `kind` `"phrase"`. A document without `kind` SHALL be read as a word, so documents written before phrases existed need no migration. A phrase's `value` SHALL have its whitespace collapsed and its `translation` entries SHALL be whole strings, never split on punctuation. Review documents SHALL reference phrases through the existing `word-id` field.

#### Scenario: Phrase document shape
- **WHEN** a phrase "auf jeden Fall" with translation "во всяком случае" is stored
- **THEN** the document `_id` is `vocab:auf jeden fall`
- **AND** it includes `type` `"vocab"`, `kind` `"phrase"`, `value`, a single-entry `translation`, `created-at`, and `modified-at`

#### Scenario: A document without a kind is a word
- **WHEN** a vocabulary document has no `kind` field
- **THEN** it is treated as a word everywhere the kind is read

#### Scenario: One value is one document
- **WHEN** a phrase is added whose value already exists as a word
- **THEN** the translations merge into that document and its `kind` is left unchanged

#### Scenario: Phrase reviews reference the phrase id
- **WHEN** a phrase trial is graded
- **THEN** the review document's `word-id` is the phrase document's `_id`

### Requirement: Review documents are stored
The system SHALL store review documents linked to vocabulary words with an ISO 8601 creation timestamp.

#### Scenario: Review document shape
- **WHEN** a review is recorded
- **THEN** the document includes `type`, `word-id`, `retained`, `created-at`, and `translation`

Example:
```json
{
  "type": "review",
  "word-id": "<vocab-id>",
  "retained": true,
  "created-at": "2026-01-20T10:00:00.000Z",
  "translation": [{"lang": "en", "value": "dog"}]
}
```

### Requirement: Example documents are stored
The system SHALL store example documents linked to vocabulary words with an ISO 8601 creation timestamp. Example documents MAY carry a `collection-id` field that scopes the example to a named collection; absence of the field means the example was generated under All Words (see `specs/examples-schema/spec.md`).

#### Scenario: Example document shape
- **WHEN** an example is stored
- **THEN** the document includes `type`, `word-id`, `word`, `value`, `translation`, `structure`, and `created-at`
- **AND** the document optionally includes `collection-id` when generated under a named collection

Example:
```json
{
  "type": "example",
  "word-id": "<vocab-id>",
  "collection-id": "collection-abc123",
  "word": "der Hund",
  "value": "Der Hund schlaeft unter dem Tisch.",
  "translation": "The dog sleeps under the table.",
  "structure": [
    {"usedForm": "Hund", "dictionaryForm": "der Hund", "translation": "dog", "wordIndex": 1}
  ],
  "created-at": "2026-01-20T10:02:00.000Z"
}
```

### Requirement: Lesson documents are stored
The system SHALL store lesson documents with an options object containing configuration settings.

#### Scenario: Lesson document with options
- **WHEN** a lesson is stored
- **THEN** the document includes `type`, `started-at`, `options`, `trials`, `remaining-trials`, `current-trial`, and `last-result`
- **AND** `options` includes `trial-selector` with values `"first"` or `"random"`

Example:
```json
{
  "_id": "lesson",
  "type": "lesson",
  "started-at": "2026-01-20T10:00:00.000Z",
  "options": {
    "trial-selector": "random"
  },
  "trials": [
    {
      "type": "word",
      "word-id": "<vocab-id>",
      "prompt": "dog",
      "answer": "der Hund",
      "locked?": false
    }
  ],
  "remaining-trials": [
    {
      "type": "word",
      "word-id": "<vocab-id>",
      "prompt": "dog",
      "answer": "der Hund",
      "locked?": false
    }
  ],
  "current-trial": {
    "type": "word",
    "word-id": "<vocab-id>",
    "prompt": "dog",
    "answer": "der Hund",
    "locked?": false
  },
  "last-result": null
}
```

### Requirement: Lesson trials include prompt, answer, and lock state
The system SHALL store lesson trials with prompt and answer strings plus a lock flag used for lesson selection.

#### Scenario: Lesson trial shape
- **WHEN** a lesson trial is stored
- **THEN** it includes `type`, `word-id`, `prompt`, `answer`, and `locked?`
- **AND** word trials are stored with `locked?` false
- **AND** example trials may be stored with `locked?` true until unlocked by lesson progress

Example:
```json
{
  "type": "word",
  "word-id": "<vocab-id>",
  "prompt": "dog",
  "answer": "der Hund",
  "locked?": false
}
```

### Requirement: Task documents are stored
The system SHALL store task documents for asynchronous work with ISO 8601 scheduling and creation timestamps.

#### Scenario: Task document shape
- **WHEN** a task is stored
- **THEN** it includes `type`, `task-type`, `word-id`, `attempts`, `run-at`, and `created-at`

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "word-id": "<vocab-id>",
  "attempts": 0,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z"
}
```

### Requirement: Dead-lettered tasks are recorded
The system SHALL record failed tasks as dead-lettered task documents with an ISO 8601 failure timestamp.

#### Scenario: Dead-letter task shape
- **WHEN** a task is dead-lettered
- **THEN** it includes `status`, `failure-reason`, and `failed-at`

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "word-id": "<vocab-id>",
  "attempts": 1,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z",
  "status": "failed",
  "failure-reason": "unknown-task-type",
  "failed-at": "2026-01-20T10:06:00.000Z"
}
```

### Requirement: Lesson trial selection uses options
The system SHALL use the trial-selector from lesson options to determine trial selection behavior.

#### Scenario: Trial selection with options
- **WHEN** selecting trials for a lesson
- **THEN** the selection uses the `trial-selector` value from `options`
- **AND** defaults to `"random"` if options or trial-selector is missing
