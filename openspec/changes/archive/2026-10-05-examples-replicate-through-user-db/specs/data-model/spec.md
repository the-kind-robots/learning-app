## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Examples kept on the device move to user-db

Examples that an earlier build stored in `device-db` SHALL move to `user-db`. Every one of them SHALL be written to `user-db` under the id its content gives it (`specs/examples-schema/spec.md`): every distinct example is kept, and identical examples become one document. That includes the examples of a word or a collection deleted since, which are kept like any other (`specs/learner-data-memory/spec.md`, `specs/collections-data-model/spec.md`). An example `user-db` holds already SHALL NOT be written again. Each `device-db` example SHALL then be deleted once `user-db` holds it.

The move SHALL run after memory is loaded and SHALL NOT hold up the start: no screen waits for it, and it is not a database migration that runs before the databases open. The example backfill and the example fetches wait for it (`specs/example-backfill/spec.md`). The move SHALL write and delete a page of examples at a time, and SHALL let other work run between pages. A failed move SHALL be logged and run again after a wait, until one succeeds; a `device-db` example that was not written stays for that run. The move SHALL be safe to run again and safe to interrupt at any point: run again, it writes nothing that is already there and deletes what an interrupted run left behind.

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
