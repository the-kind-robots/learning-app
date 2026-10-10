## ADDED Requirements

### Requirement: The app keeps no index
The app SHALL create no index in user-db or in device-db, and SHALL bring none up to date; so the splash waits for none. An index an earlier build left in user-db SHALL be neither queried nor brought up to date; the one an earlier build's task queue kept in device-db SHALL be removed at start (Requirement: Examples kept on the device move to user-db).

#### Scenario: A new installation
- **WHEN** the app starts on a device with empty databases and memory is loaded
- **THEN** `getIndexes()` on user-db lists `_all_docs` only
- **AND** `getIndexes()` on device-db lists `_all_docs` only

#### Scenario: An index an earlier build left in user-db
- **WHEN** the app starts on a device whose user-db holds `_design/by-type` from an earlier build
- **THEN** the app neither queries that index nor brings it up to date

## MODIFIED Requirements

### Requirement: Examples kept on the device move to user-db

Examples that an earlier build stored in `device-db` SHALL move to `user-db`. Every one of them SHALL be written to `user-db` under the id its content gives it (`specs/examples-schema/spec.md`): every distinct example is kept, and identical examples become one document. That includes the examples of a word or a collection deleted since, which are kept like any other (`specs/learner-data-memory/spec.md`, `specs/collections-data-model/spec.md`). An example `user-db` holds already SHALL NOT be written again. Each `device-db` example SHALL then be deleted once `user-db` holds it.

The same run SHALL delete every task document an earlier build's task queue left in `device-db`, whatever its id, a page per write with other work let run between writes, and SHALL remove the indexes earlier builds kept there: the task queue's (`_design/by-type-run-at-created-at`) and the `by-type` index builds before #526 made in every database. A task document carries nothing the learner entered: the pairs it asked for are read from memory again (`specs/example-backfill/spec.md`).

The move SHALL run after memory is loaded and SHALL NOT hold up the start: no screen waits for it, and it is not a database migration that runs before the databases open. Example fetching waits for it (`specs/example-backfill/spec.md`). The move SHALL write and delete a page of examples at a time, and SHALL let other work run between pages. A failed move SHALL be logged and run again after a wait, until one succeeds; a `device-db` example that was not written stays for that run. The move SHALL be safe to run again and safe to interrupt at any point: run again, it writes nothing that is already there and deletes what an interrupted run left behind.

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

## REMOVED Requirements

### Requirement: The splash waits for no index

**Reason**: It allowed device-db the task queue's index; there is no queue, so no database keeps an
index.
**Migration**: Requirement: The app keeps no index.

### Requirement: Task documents are stored

**Reason**: No task documents are written: example fetching keeps its state in memory
(`specs/example-backfill/spec.md`).
**Migration**: Leftover task documents are deleted at start (Requirement: Examples kept on the
device move to user-db).

### Requirement: Dead-lettered tasks are recorded

**Reason**: No task documents are written, so none is dead-lettered. A pair that failed is not
asked for again in the page's life (`specs/example-backfill/spec.md`).
**Migration**: Leftover dead letters are task documents and are deleted at start.
