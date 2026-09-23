## ADDED Requirements

### Requirement: A replication pass queues the example fetches the device owes

After a completed replication pass the device SHALL queue an example-fetch task for every word it
holds that has no example where one is looked up. Examples live in `device-db` and never replicate,
so a word that arrived by replication carries no example until the receiving device asks for one.

The pairs a fetch is owed for follow the lookup rules in `specs/examples-schema/spec.md`:

- for every named collection and every word in it, when no example carries that word and that
  collection — a named collection's lookup is strict by `collection-id`, so an example generated
  elsewhere does not answer it;
- for every word that belongs to no collection, when no example carries that word at all — the main
  card is a union view, so any example for the word answers it.

A word already covered by an existing example SHALL NOT be queued.

#### Scenario: A word arrived by replication without its example

- **WHEN** a replication pass completes
- **AND** a word in the vocabulary has no example document
- **THEN** an example-fetch task is queued for it

#### Scenario: A word that already has its example

- **WHEN** a replication pass completes
- **AND** a word's example is already stored for the pair it is looked up under
- **THEN** no example-fetch task is queued for it

#### Scenario: A word in a collection whose example came from another collection

- **WHEN** a word belongs to collection T and its only example carries a different collection, or none
- **THEN** an example-fetch task is queued for that word with `collection-id = T` and T's name

#### Scenario: A word in no collection with any example

- **WHEN** a word belongs to no collection and an example exists for it under some collection
- **THEN** no task is queued — the main card's union lookup already answers

### Requirement: A backfill pass is bounded and does not queue what is already queued

A backfill pass SHALL queue at most a fixed number of tasks, and SHALL NOT queue a pair an
example-fetch task is already waiting on. What the cap leaves over is queued by a later pass. The
task queue runs a few fetches at a time and retries with backoff; a first synchronisation carrying a
whole vocabulary would otherwise become one burst of provider requests, and a pass repeated before
the queue drains would duplicate every one of them.

#### Scenario: More words are owed than the cap allows

- **WHEN** a backfill pass finds more missing pairs than the cap
- **THEN** it queues exactly the cap
- **AND** a later pass queues from what is left

#### Scenario: A pass repeated before the queue drains

- **WHEN** a backfill pass runs while example-fetch tasks are still queued
- **THEN** no task is queued for a pair one of those tasks already carries

### Requirement: Backfill never fails the replication pass

The backfill SHALL be contained: a failure while deciding or queueing SHALL be logged and SHALL NOT
change what the pass reports to its caller. Queueing writes task documents locally and issues no
network request of its own, so an unreachable backend delays the fetches, it does not break the pass.

#### Scenario: The backfill throws

- **WHEN** reading local data or writing a task fails during backfill
- **THEN** the pass still reports what it replicated

#### Scenario: The device is offline

- **WHEN** a pass completes and the backend is unreachable
- **THEN** the tasks are queued and wait, and the pass is unaffected
