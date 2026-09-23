## ADDED Requirements

### Requirement: A start queues the example fetches the device already owes

On start the device SHALL count its debt over every word it holds and queue what it owes, bounded by
the cap. This is the one full reading: it closes what earlier runs left, whatever the reason — the
cap, a device that was offline, a failure.

#### Scenario: A device starts holding words without examples

- **WHEN** the application starts
- **THEN** every word it holds is considered, and an example-fetch task is queued for each owed pair
  up to the cap

#### Scenario: A device starts owing nothing

- **WHEN** the application starts and every pair already has its example or its queued task
- **THEN** no task is queued

## MODIFIED Requirements

### Requirement: A replication pass queues the example fetches the device owes

After a completed replication pass the device SHALL count its debt over what that pass brought home,
and SHALL NOT read the rest of the vocabulary. Examples live in `device-db` and never replicate, so
a document that arrived by replication can leave a pair without an example here.

A pass brings two kinds of debt, and both SHALL be counted:

- a word that arrived — it needs an example for every named collection holding it, or, when it
  belongs to none, one example of its own;
- a collection that arrived — every word it names may have been on this device all along and been
  put into that collection on another device, and no example here carries that pair.

The pairs a fetch is owed for follow the lookup rules in `specs/examples-schema/spec.md`:

- for every named collection and every word in it, when no example carries that word and that
  collection — a named collection's lookup is strict by `collection-id`, so an example generated
  elsewhere does not answer it;
- for every word that belongs to no collection, when no example carries that word at all — the main
  card is a union view, so any example for the word answers it.

A word already covered by an existing example SHALL NOT be queued. The same rule SHALL decide a
single pair when a word is added to a collection by hand, so both answers agree.

#### Scenario: A word arrived by replication without its example

- **WHEN** a replication pass brings a word document
- **AND** that word has no example document
- **THEN** an example-fetch task is queued for it

#### Scenario: A collection arrived naming a word this device already held

- **WHEN** a replication pass brings a collection document naming word W
- **AND** no example carries W and that collection
- **THEN** an example-fetch task is queued for W with that collection and its name

#### Scenario: A word the pass did not bring

- **WHEN** a replication pass completes
- **AND** a word the pass did not bring, and that no collection the pass brought names, has no
  example
- **THEN** no task is queued for it by this pass

#### Scenario: A pass that pulled nothing

- **WHEN** a replication pass completes having written nothing on this device — a push-only pass
- **THEN** no debt is counted and the vocabulary is not read

#### Scenario: A word that already has its example

- **WHEN** a replication pass brings a word whose example is already stored for the pair it is
  looked up under
- **THEN** no example-fetch task is queued for it

#### Scenario: A word in a collection whose example came from another collection

- **WHEN** a word belongs to collection T and its only example carries a different collection, or none
- **THEN** an example-fetch task is queued for that word with `collection-id = T` and T's name

#### Scenario: A word in no collection with any example

- **WHEN** a word belongs to no collection and an example exists for it under some collection
- **THEN** no task is queued — the main card's union lookup already answers

#### Scenario: A word added by hand to a collection that has no example for it

- **WHEN** a word already in the vocabulary is added to a named collection
- **AND** no example carries that word and that collection
- **THEN** an example-fetch task is queued for that pair, as a backfill pass would queue it

### Requirement: A backfill pass is bounded and does not queue what is already queued

A backfill pass SHALL queue at most a fixed number of tasks, and SHALL NOT queue a pair an
example-fetch task is already waiting on. What the cap leaves over is queued by a later pass that
pulls, or by the next start. The task queue runs a few fetches at a time and retries with backoff; a
first synchronisation carrying a whole vocabulary would otherwise become one burst of provider
requests, and a pass repeated before the queue drains would duplicate every one of them.

#### Scenario: More words are owed than the cap allows

- **WHEN** a backfill pass finds more missing pairs than the cap
- **THEN** it queues exactly the cap
- **AND** the next start queues from what is left

#### Scenario: A pass repeated before the queue drains

- **WHEN** a backfill pass runs while example-fetch tasks are still queued
- **THEN** no task is queued for a pair one of those tasks already carries
