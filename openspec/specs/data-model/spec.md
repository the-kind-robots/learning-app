## Purpose
The data model spec defines the required document shapes stored in the local database.

## Requirements

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
The system SHALL store example documents linked to vocabulary words. Example documents MAY carry a `collection-id` field that scopes the example to a named collection; absence of the field means the example was generated under All Words (see `specs/examples-schema/spec.md`). Example documents SHALL live in `user-db` and replicate with the rest of the account's data, so a device that receives a word by replication receives the examples generated for it on other devices. An example document SHALL hold nothing that depends on the device or on the time it was stored, so that the same example stored on two devices is the same document (`specs/examples-schema/spec.md`).

#### Scenario: Example document shape
- **WHEN** an example is stored
- **THEN** the document includes `type`, `word-id`, `word`, `value`, `translation` and `structure`
- **AND** the document optionally includes `collection-id` when generated under a named collection
- **AND** the document has no creation time

Example:
```json
{
  "_id": "example:<vocab-id>:collection-abc123:<content hash>",
  "type": "example",
  "word-id": "<vocab-id>",
  "collection-id": "collection-abc123",
  "word": "der Hund",
  "value": "Der Hund schlaeft unter dem Tisch.",
  "translation": "The dog sleeps under the table.",
  "structure": [
    {"dictionaryForm": "der Hund", "translation": "dog", "usedForm": "Hund", "wordIndex": 1}
  ]
}
```

#### Scenario: An example replicates
- **WHEN** an example is stored on a device that has an account
- **AND** another device of the account completes a replication pass
- **THEN** the other device holds that example

### Requirement: Lesson trial selection uses options
The system SHALL use the trial-selector from lesson options to determine trial selection behavior.

#### Scenario: Trial selection with options
- **WHEN** selecting trials for a lesson
- **THEN** the selection uses the `trial-selector` value from `options`
- **AND** defaults to `"random"` if options or trial-selector is missing

### Requirement: A migration reads every document it claims to migrate

A migration that copies or rewrites stored documents SHALL read every document matching
its query, not the first page of them. Queries backing a migration SHALL NOT rely on the
storage layer's default result limit.

#### Scenario: A type with more documents than the default page size

- **WHEN** the source database holds more documents of a migrated type than the storage
  layer's default query limit
- **THEN** every one of them is copied to the destination database
- **AND** the count in the destination equals the count in the source

#### Scenario: An empty source database

- **WHEN** a migration runs against a source database that holds no documents
- **THEN** it completes without error and copies nothing

### Requirement: user-db design documents are not replicated
user-db design documents SHALL NOT replicate: a sync pass SHALL carry user documents in both directions and no document whose id begins with `_design/` in either direction.

#### Scenario: A sync pass leaves design documents behind
- **WHEN** a sync pass runs against the account's copy on the server
- **THEN** the remote copy holds no `_design/` documents
- **AND** every user document written on either side is present on the other

### Requirement: Examples kept on the device move to user-db

Examples that an earlier build stored in `device-db` SHALL move to `user-db`. Every one of them SHALL be written to `user-db` under the id its content gives it (`specs/examples-schema/spec.md`): every distinct example is kept, and identical examples become one document. That includes the examples of a word or a collection deleted since, which are kept like any other (`specs/learner-data-memory/spec.md`, `specs/collections-data-model/spec.md`). An example `user-db` holds already SHALL NOT be written again. Each `device-db` example SHALL then be deleted once `user-db` holds it.

A database migration, run once per device and recorded in `device-db`, SHALL delete every task document an earlier build's task queue left in `device-db`, whatever its id, a page per write with other work let run between writes, and SHALL remove the indexes earlier builds kept there: the task queue's (`_design/by-type-run-at-created-at`) and the `by-type` index builds before #526 made in every database. A task document carries nothing the learner entered: the pairs it asked for are read from memory again (`specs/example-backfill/spec.md`).

The move of the examples SHALL run after memory is loaded and SHALL NOT hold up the start: no screen waits for it, and it is not a database migration that runs before the databases open. Example fetching waits for it (`specs/example-backfill/spec.md`). The move SHALL write and delete a page of examples at a time, and SHALL let other work run between pages. A failed move SHALL be logged and run again after a wait, until one succeeds; a `device-db` example that was not written stays for that run. The move SHALL be safe to run again and safe to interrupt at any point: run again, it writes nothing that is already there and deletes what an interrupted run left behind.

#### Scenario: A device holding examples from an earlier build
- **WHEN** the app starts on a device whose `device-db` holds examples
- **THEN** once the move completes, `user-db` holds each of those examples
- **AND** `device-db` holds no example

#### Scenario: Two different examples of one pair
- **WHEN** `device-db` holds two different examples for the same entry and collection
- **THEN** `user-db` holds both

#### Scenario: The same example twice
- **WHEN** `device-db` holds the same example twice, under two ids
- **THEN** `user-db` holds it once

#### Scenario: An example of a deleted word or collection
- **WHEN** `device-db` holds an example of a word or a collection that has since been deleted
- **THEN** `user-db` holds that example once the move completes

#### Scenario: Interrupted after the write
- **WHEN** a move wrote examples to `user-db` and stopped before it deleted the `device-db` copies
- **AND** the move runs again
- **THEN** `device-db` holds no example and nothing `user-db` held is written again

#### Scenario: Run twice
- **WHEN** the move runs on a device whose examples have already moved
- **THEN** nothing is written

#### Scenario: A failed move
- **WHEN** writing to `user-db` fails during the move
- **THEN** the `device-db` examples not written stay, and the move runs again after a wait

#### Scenario: The start does not wait
- **WHEN** the app starts on a device whose `device-db` holds examples
- **THEN** the screen asked for is shown once memory is loaded, whether or not the move has finished

#### Scenario: Leftover tasks
- **WHEN** the app starts on a device whose `device-db` holds task documents — under `task:` ids and
  under generated ids — the task queue's index and the `by-type` index
- **THEN** the databases open and the screen asked for is shown without waiting for them to be deleted
- **AND** shortly after the start, `device-db` holds no task document and no `_design/` document
- **AND** a later start does not look for them again
- **AND** the identity and the migration records stay

#### Scenario: A failed sweep
- **WHEN** deleting the leftover tasks or indexes fails
- **THEN** the sweep is not recorded as done, and runs again at the next start

### Requirement: The app keeps no index
The app SHALL create no index in user-db or in device-db, and SHALL bring none up to date; so the splash waits for none. An index an earlier build left in user-db SHALL be neither queried nor brought up to date; the one an earlier build's task queue kept in device-db SHALL be removed by a sweep that runs once per device, in the background after the databases open, and never holds up the start (Requirement: Examples kept on the device move to user-db).

#### Scenario: A new installation
- **WHEN** the app starts on a device with empty databases and memory is loaded
- **THEN** `getIndexes()` on user-db lists `_all_docs` only
- **AND** `getIndexes()` on device-db lists `_all_docs` only

#### Scenario: An index an earlier build left in user-db
- **WHEN** the app starts on a device whose user-db holds `_design/by-type` from an earlier build
- **THEN** the app neither queries that index nor brings it up to date
