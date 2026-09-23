## MODIFIED Requirements

### Requirement: A replication pass queues the example fetches the device owes

After a completed replication pass the device SHALL count what is missing over what that pass
brought home, and SHALL NOT read the rest of the vocabulary. Examples live in `device-db` and never
replicate, so a document that arrived by replication can leave a pair without an example here.

What is missing is counted over vocabulary entries, and a phrase is one: words and phrases are the
same document type and ask for an example alike (`specs/examples/spec.md`). Nothing in this
requirement reads an entry's kind.

A pass SHALL count the entries it brought, and SHALL NOT unfold a collection it brought into the
entries that collection names. A theme document is rewritten whole every time anyone adds an entry
to it on any device, so unfolding would put every entry in that theme in question on every such
change. An entry put into a theme on another device gets that theme's example at the next start.

The pairs a fetch is owed for follow the lookup rules in `specs/examples-schema/spec.md`:

- for every named collection and every entry in it, when no example carries that entry and that
  collection — a named collection's lookup is strict by `collection-id`, so an example generated
  elsewhere does not answer it;
- for every entry that belongs to no collection, when no example carries that entry at all — the
  main card is a union view, so any example for the entry answers it.

An entry already covered by an existing example SHALL NOT be queued. The same rule SHALL decide a
single pair when an entry is added to a collection by hand, so both answers agree.

#### Scenario: A word arrived by replication without its example

- **WHEN** a replication pass brings a word document
- **AND** that word has no example document
- **THEN** an example-fetch task is queued for it

#### Scenario: A phrase arrived by replication without its example

- **WHEN** a replication pass brings a phrase document
- **AND** that phrase has no example document
- **THEN** an example-fetch task is queued for it, carrying the phrase's own Russian translations

#### Scenario: A phrase in a named collection

- **WHEN** a phrase belongs to collection T and its only example carries a different collection, or none
- **THEN** an example-fetch task is queued for that phrase with `collection-id = T` and T's name

#### Scenario: A collection arrived naming an entry this device already held

- **WHEN** a replication pass brings a collection document naming entry W
- **AND** the pass brought no document for W itself
- **THEN** no task is queued by this pass
- **AND** the next start queues the pair (W, that collection) if no example answers it

#### Scenario: A word the pass did not bring

- **WHEN** a replication pass completes
- **AND** a word the pass did not bring has no example
- **THEN** no task is queued for it by this pass

#### Scenario: A pass that pulled nothing

- **WHEN** a replication pass completes having written nothing on this device — a push-only pass
- **THEN** nothing is counted and the vocabulary is not read

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

### Requirement: Backfill never fails the replication pass

The backfill SHALL be contained: a failure while deciding or queueing SHALL be logged and SHALL NOT
change what the pass reports to its caller. A pass SHALL NOT wait for the backfill either: what the
pass reports is what it replicated, and the screen and the pass throttle follow the replication, not
the queueing that comes after it. Queueing writes task documents locally and issues no network
request of its own, so an unreachable backend delays the fetches, it does not break the pass.

#### Scenario: The backfill throws

- **WHEN** reading local data or writing a task fails during backfill
- **THEN** the pass still reports what it replicated

#### Scenario: The backfill is still running

- **WHEN** a replication pass completes and the backfill it starts has not finished
- **THEN** the pass reports what it replicated without waiting for it

#### Scenario: The device is offline

- **WHEN** a pass completes and the backend is unreachable
- **THEN** the tasks are queued and wait, and the pass is unaffected

### Requirement: A start queues the example fetches the device already owes

On start the device SHALL count what is missing over every vocabulary entry it holds — words and
phrases alike — and queue all of it. This is the one full reading: it closes whatever earlier runs
left, whatever the reason — a device that was offline, a failure, an entry themed on another device.

#### Scenario: A device starts holding entries without examples

- **WHEN** the application starts
- **THEN** every entry it holds is considered, and an example-fetch task is queued for each missing
  pair

#### Scenario: A device starts owing nothing

- **WHEN** the application starts and every pair already has its example or its queued task
- **THEN** no task is queued

## REMOVED Requirements

### Requirement: A backfill pass is bounded and does not queue what is already queued

**Reason**: The cap guarded against a burst the task queue already prevents — a few fetches at a
time, backoff and Retry-After — while leaving a remainder no trigger was responsible for. Replaced
by "A backfill queues every missing pair and nothing already queued".

**Migration**: None. A device that starts on the new build queues what the cap had left behind.

## ADDED Requirements

### Requirement: A backfill queues every missing pair and nothing already queued

A backfill SHALL queue a task for every pair it finds missing, with no cap, and SHALL NOT queue a
pair a live example-fetch task already carries. Queueing writes task documents and generates
nothing: the pace belongs to the task queue, which runs a few fetches at a time and backs off on the
provider's terms, so a cap here would only leave a remainder nobody is responsible for.

A task that was dead-lettered SHALL NOT count as queued. The queue will never run it again, so its
pair would otherwise never be asked for.

The tasks of one backfill SHALL be written together rather than one at a time — a device catching up
on a whole vocabulary queues as many as it is missing.

#### Scenario: A device is missing more pairs than any cap would allow

- **WHEN** a start finds a hundred and twenty missing pairs
- **THEN** a hundred and twenty example-fetch tasks are queued
- **AND** nothing is left for a later pass to pick up

#### Scenario: A pass repeated before the queue drains

- **WHEN** a backfill runs while example-fetch tasks are still queued
- **THEN** no task is queued for a pair one of those live tasks already carries

#### Scenario: A fetch that was dead-lettered

- **WHEN** an example-fetch task has been dead-lettered for a pair
- **AND** a backfill runs
- **THEN** the pair counts as missing and a task is queued for it
