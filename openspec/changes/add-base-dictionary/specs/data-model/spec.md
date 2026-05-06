## MODIFIED Requirements
### Requirement: Vocabulary documents are stored
The system SHALL store vocabulary documents with translations, ISO 8601 timestamps for creation and modification, and optional metadata.

#### Scenario: Vocabulary document shape
- **WHEN** a vocabulary word is stored
- **THEN** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`
- **AND** the document MAY include `meta`

Example:
```json
{
  "type": "vocab",
  "value": "der Hund",
  "translation": [{"lang": "ru", "value": "sobaka"}],
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z",
  "meta": {"source": "dictionary"}
}
```

### Requirement: Task documents are stored
The system SHALL store task documents for asynchronous work with ISO 8601 scheduling and creation timestamps and an optional data payload.

#### Scenario: Task document shape
- **WHEN** a task is stored
- **THEN** it includes `type`, `task-type`, `attempts`, `run-at`, and `created-at`
- **AND** it MAY include `data` for task-specific payloads

Example:
```json
{
  "type": "task",
  "task-type": "example-fetch",
  "data": {"word-id": "<vocab-id>"},
  "attempts": 0,
  "run-at": "2026-01-20T10:05:00.000Z",
  "created-at": "2026-01-20T10:00:00.000Z"
}
```

## ADDED Requirements
### Requirement: Dictionary entry documents are stored
The system SHALL store dictionary entry documents with translations, canonical values, and ISO 8601 timestamps for creation and modification.

The `_id` follows the convention `lemma:<normalized-value>:<pos>`, which deterministically encodes the lookup key into the document ID. This enables primary-index queries and handles homonyms via the article in the normalized value.

#### Scenario: Dictionary entry document shape
- **WHEN** a dictionary entry is stored
- **THEN** the document `_id` follows the `lemma:<normalized-value>:<pos>` convention
- **AND** the document includes `type`, `value`, `translation`, `created-at`, and `modified-at`
- **AND** it MAY include `meta` for level, part-of-speech, normalized values, forms, rank, and source

Example:
```json
{
  "_id": "lemma:der hund:noun",
  "type": "dictionary-entry",
  "value": "der Hund",
  "translation": [{"lang": "ru", "value": "собака"}],
  "meta": {
    "level": "a1",
    "pos": "noun",
    "normalized-value": "der hund",
    "forms": ["Hunde"],
    "rank": 100,
    "source": "example-source"
  },
  "created-at": "2026-01-20T10:00:00.000Z",
  "modified-at": "2026-01-20T10:10:00.000Z"
}
```

### Requirement: Surface-form documents are stored
The system SHALL store surface-form documents that map normalized inflected forms to dictionary entries for fast prefix-based autocomplete.

The `_id` follows the convention `sf:<normalized-form>`. Each document contains an `entries` array with denormalized entry data (`lemma-id`, `lemma`, `rank`) for fast primary lookup; translation enrichment may use a batch lookup by `lemma-id`.

#### Scenario: Surface-form document shape
- **WHEN** a surface-form is stored
- **THEN** the document `_id` follows the `sf:<normalized-form>` convention
- **AND** the document includes `type` and `entries`
- **AND** each entry in `entries` includes `lemma-id`, `lemma`, and `rank`

Example:
```json
{
  "_id": "sf:hunde",
  "type": "surface-form",
  "entries": [
    {
      "lemma-id": "lemma:die größe:noun",
      "lemma": "die Größe",
      "rank": 100
    }
  ]
}
```

#### Scenario: Surface-form collision (multiple entries)
- **WHEN** a normalized form maps to multiple dictionary entries
- **THEN** the `entries` array contains one element per distinct entry

Example:
```json
{
  "_id": "sf:masse",
  "type": "surface-form",
  "entries": [
    { "lemma-id": "lemma:das mass:noun", "lemma": "das Maß", "rank": 80 },
    { "lemma-id": "lemma:die masse:noun", "lemma": "die Masse", "rank": 60 }
  ]
}
```

### Requirement: Dictionary meta document is stored
The system SHALL store a dictionary meta document that tracks import metadata.

#### Scenario: Dictionary meta document shape
- **WHEN** the dictionary import completes
- **THEN** the document includes `type`, `schema-version`, `dictionary-epoch`, `generated-at`, `manifest-sha256`, and `files`

Example:
```json
{
  "_id": "dictionary-meta",
  "type": "dictionary-meta",
  "schema-version": 2,
  "dictionary-epoch": "dictionary-v2-raw-lemma-ids",
  "generated-at": "2026-02-01T18:28:42.528Z",
  "enriched-at": "2026-05-06T10:14:52Z",
  "manifest-sha256": "sha256:example",
  "files": {
    "dictionary-entries.jsonl": {"count": 123, "bytes": 456, "sha256": "sha256:entries"},
    "surface-forms.jsonl": {"count": 789, "bytes": 1011, "sha256": "sha256:forms"}
  }
}
```
