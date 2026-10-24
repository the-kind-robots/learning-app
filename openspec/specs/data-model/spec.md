## Purpose
The data model spec defines the required document shapes stored in the local database.

## Requirements

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

### Requirement: The app keeps no index
The app SHALL create no index in user-db or in device-db, and SHALL bring none up to date; so the splash waits for none. An index an earlier build left in user-db or in device-db, such as the one its task queue kept, SHALL be neither queried nor brought up to date. A start SHALL NOT move examples out of device-db, delete task documents or indexes from it, or otherwise tidy it.

#### Scenario: A new installation
- **WHEN** the app starts on a device with empty databases and memory is loaded
- **THEN** `getIndexes()` on user-db lists `_all_docs` only
- **AND** `getIndexes()` on device-db lists `_all_docs` only

#### Scenario: An index an earlier build left in user-db
- **WHEN** the app starts on a device whose user-db holds `_design/by-type` from an earlier build
- **THEN** the app neither queries that index nor brings it up to date

#### Scenario: A start leaves device-db as it is
- **WHEN** the app starts on a device whose `device-db` holds example or task documents from an earlier build
- **THEN** no example is written to `user-db` for them and no document is deleted from `device-db`
- **AND** the screen asked for is shown once memory is loaded

### Requirement: Vocab conflict resolution unions translations
The system SHALL resolve a vocab document revision conflict by unioning the translations of the conflicting revisions, not by plain last-write-wins.

#### Scenario: Concurrent translations merge
- **WHEN** two devices add the same word with different translations and sync
- **THEN** the resolved document contains the union of both translation sets
- **AND** neither side's translation is dropped

### Requirement: A word keeps the part of speech of the suggestion it was picked from
A vocabulary document of a word added from a picked suggestion SHALL carry `pos`, the part of speech of that suggestion as the dictionary names it. The part of speech SHALL count only while the word field still holds the picked lemma; a word typed, or typed on after the pick, SHALL be stored without `pos`. A phrase SHALL NOT carry `pos`. `pos` is optional, so documents written before it need no migration, and a word added again whose document exists SHALL keep the stored document's `pos` as it is.

#### Scenario: A picked word keeps its part of speech
- **WHEN** a word is added after the adjective suggestion of "schön" was picked
- **THEN** its document carries the adjective's `pos`

#### Scenario: A typed word has none
- **WHEN** a word is added without a suggestion picked, or after the picked lemma was edited
- **THEN** its document has no `pos`

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
