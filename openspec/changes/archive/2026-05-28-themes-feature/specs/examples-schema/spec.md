# examples-schema Specification

## Purpose
Define the example document shape with `collection-id`, per-collection lookup semantics, union behaviour on All Words, cross-collection re-fetch, and backend AI prompt injection.

## ADDED Requirements

### Requirement: Example documents include collection-id when generated under a named collection
The system SHALL store a `collection-id` field on example documents generated while a named (non-All-Words) collection is active.

#### Scenario: New example document under named collection includes collection-id
- **WHEN** an example is fetched for word W while collection T is active
- **THEN** the stored document includes `type: "example"`, `word-id`, `collection-id`, `word`, `value`, `translation`, `structure`, and `created-at`

Example:
```json
{
  "type": "example",
  "word-id": "<vocab-id>",
  "collection-id": "collection-abc123",
  "word": "der Hund",
  "value": "Der Hund rennt über die Straße.",
  "translation": "The dog runs across the street.",
  "created-at": "2026-05-22T10:00:00.000Z"
}
```

#### Scenario: Example under All Words has no collection-id field
- **WHEN** an example is fetched for word W while All Words is active
- **THEN** the stored document is written without a `collection-id` field

### Requirement: Named-collection lookup is strict by collection-id
The system SHALL return only examples whose `collection-id` matches the named collection being queried.

#### Scenario: Themed-card lookup returns own-collection examples only
- **WHEN** examples are queried for `(word-id, collection-T)`
- **THEN** the result includes only documents whose `collection-id` equals `collection-T`
- **AND** documents without a `collection-id` or with a different `collection-id` are excluded

### Requirement: All Words lookup is a union across collection-ids
The system SHALL return any example for the given word when the active collection is All Words (`nil`/`"all-words"`), regardless of `collection-id`. Legacy documents without a `collection-id` field surface naturally on All Words via the same union.

#### Scenario: All Words returns examples across all collections
- **WHEN** examples are queried for `(word-id, nil)`
- **THEN** the result includes documents with no `collection-id`, with `collection-id == T1`, `collection-id == T2`, etc. — any document with the matching `word-id`

#### Scenario: Legacy example surfaces on All Words
- **WHEN** an example document lacks a `collection-id` field
- **AND** the All Words lookup runs for its `word-id`
- **THEN** the document is included in the result

### Requirement: Backend endpoint accepts optional collection parameter
The system SHALL forward an optional `context` query parameter from `/api/examples` to the AI prompt generator. If the parameter is absent or blank, no collection injection occurs.

#### Scenario: Collection name injected into prompt
- **WHEN** `/api/examples` is called with `context=Driving+School`
- **THEN** the AI prompt includes the collection name as additional context

#### Scenario: No injection for All Words
- **WHEN** `/api/examples` is called without a `context` parameter
- **THEN** the AI prompt is the neutral default (no collection context)

### Requirement: Example fetch task carries collection-id and collection-name
The system SHALL include `collection-id` and `collection-name` in the example-fetch task payload so that the task runner can both filter by collection on read and inject the collection name into the backend request.

#### Scenario: Task payload includes collection metadata
- **WHEN** an example-fetch task is created for word W under collection T
- **THEN** the task document's `data` field includes `word-id`, `collection-id`, and `collection-name`

### Requirement: Cross-collection re-fetch generates context-specific example
The system SHALL generate a new context-specific example when an existing word is added to a different named collection that does not yet have an example for that word.

#### Scenario: Existing word added to a new themed collection
- **WHEN** a word already exists in vocabulary
- **AND** the user adds it to a named collection T that has no example for that `(word-id, T)` pair
- **THEN** an example-fetch task is queued with `collection-id = T` and `collection-name = T.name`
- **AND** the resulting stored example carries `collection-id = T`

#### Scenario: Existing word already has example in target collection — no duplicate fetch
- **WHEN** the user adds an existing word to a named collection that already has an example for that `(word-id, collection-id)` pair
- **THEN** no new example-fetch task is queued

## MODIFIED Requirements

### Requirement: Example documents are stored
The system SHALL store example documents with `type`, `word-id`, `word`, `value`, `translation`, `structure`, and `created-at`, plus an optional `collection-id`. The `collection-id` field is present only when the document was generated under a named collection; documents written under All Words omit the field.

#### Scenario: Example document shape with optional collection-id
- **WHEN** an example is stored
- **THEN** the document includes all required fields above
- **AND** `collection-id` is included if and only if a named collection was active at fetch time
