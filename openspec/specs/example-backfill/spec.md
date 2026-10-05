# example-backfill Specification

## Purpose
Define how a device catches up on example sentences it is missing for its vocabulary, after replication and on start, without letting that work disturb sync.

## Requirements

### Requirement: A replication pass queues the example fetches the device owes

After a completed replication pass the device SHALL count what is missing over what that pass
brought home, and SHALL NOT read the rest of the vocabulary. It SHALL count once memory holds every
document the pass stored (`specs/learner-data-memory/spec.md`), and SHALL count from memory alone:
examples replicate with the words they belong to, so an example the pass brought answers its pair,
and a word or collection the pass deleted is gone from memory and asks for nothing.

A word often arrives a pass ahead of its example: the device that added it fetches the example and
pushes it a push window later. A fetch a pass queues SHALL therefore not be due before two of the sync
engine's push windows after the pass. A pass that brings an example SHALL delete the queued fetch of
the pair that example was made for, so a fetch whose example arrives while it waits is never sent.

What is missing is counted over vocabulary entries, and a phrase is one: words and phrases are the
same document type and ask for an example alike (`specs/examples/spec.md`). Nothing in this
requirement reads an entry's kind.

A pass SHALL count the entries it brought, and SHALL unfold a collection it brought into the entries
that collection names. A theme document is rewritten whole every time anyone adds an entry to it on
any device, so a pass that brings one is how this device learns that an entry was themed elsewhere;
what that costs is the size of the theme, which is the read a start does anyway.

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
- **THEN** an example-fetch task is queued for it, due two push windows after the pass

#### Scenario: A word arrived by replication with its example

- **WHEN** a replication pass brings a word document and the example another device fetched for it
- **THEN** no example-fetch task is queued for it
- **AND** no example request is sent for it

#### Scenario: A word's example arrives one pass after the word

- **WHEN** a replication pass brings a word without its example, and a fetch is queued for it
- **AND** a later pass brings the example before the fetch is due
- **THEN** that pass deletes the fetch, and no example request is sent

#### Scenario: A phrase arrived by replication without its example

- **WHEN** a replication pass brings a phrase document
- **AND** that phrase has no example document
- **THEN** an example-fetch task is queued for it, carrying the phrase's own Russian translations

#### Scenario: A phrase in a named collection

- **WHEN** a phrase belongs to collection T and its only example carries a different collection, or none
- **THEN** an example-fetch task is queued for that phrase with `collection-id = T` and T's name

#### Scenario: A collection arrived naming an entry this device already held

- **WHEN** a replication pass brings a collection document naming entry W
- **AND** no example carries W and that collection
- **THEN** an example-fetch task is queued for the pair (W, that collection)

#### Scenario: A collection the pass deleted

- **WHEN** a replication pass deletes a collection naming entry W
- **THEN** no task is queued for the pair (W, that collection)

#### Scenario: A word the pass did not bring

- **WHEN** a replication pass completes
- **AND** a word the pass did not bring, and that no collection the pass brought names, has no
  example
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

The backfill SHALL count only once memory is loaded, the examples kept on the device have moved to
`user-db` (`specs/data-model/spec.md`), and this session's first replication pass has completed. A
device without an account runs no pass, and counts once the move is done. So an example the move
brings, or one the account already holds on another device, answers its pair before anything is
counted. A pass that completes before then SHALL count after it too.

#### Scenario: A device starts holding entries without examples

- **WHEN** the application starts
- **THEN** every entry it holds is considered, and an example-fetch task is queued for each missing
  pair

#### Scenario: A device starts owing nothing

- **WHEN** the application starts and every pair already has its example or its queued task
- **THEN** no task is queued

#### Scenario: A device starts with examples kept on the device

- **WHEN** the application starts on a device whose `device-db` holds the example of a word
- **THEN** no example-fetch task is queued for that word's pair

#### Scenario: A device with an account starts

- **WHEN** a device with an account starts holding entries whose examples the account holds on
  another device
- **THEN** nothing is counted before the first pass of the session has completed
- **AND** no example-fetch task is queued for those entries

#### Scenario: The first pass completes before the backfill starts listening

- **WHEN** a device with an account completes its first pass before the backfill has started
- **THEN** the backfill counts once the examples kept on the device have moved, without waiting for
  another pass

### Requirement: A backfill queues every missing pair, and one pair is one task

A backfill SHALL queue a task for every pair it finds missing, with no cap. Queueing writes task
documents and generates nothing: the pace belongs to the task queue, which runs a few fetches at a
time and backs off on the provider's terms, so a cap here would only leave a remainder nobody is
responsible for.

A fetch task's identity SHALL be the pair it is for. Asking for a pair that is already queued SHALL
therefore write nothing and SHALL NOT be an error — no reader has to know what the queue holds, and
two backfills may run at once.

A task that was dead-lettered SHALL NOT hold the identity of the pair, so the pair can be asked for
again; the failure SHALL be kept under an identity of its own for reading.

The tasks of one backfill SHALL be written together rather than one at a time — a device catching up
on a whole vocabulary queues as many as it is missing.

#### Scenario: A device is missing more pairs than any cap would allow

- **WHEN** a start finds a hundred and twenty missing pairs
- **THEN** a hundred and twenty example-fetch tasks are queued
- **AND** nothing is left for a later pass to pick up

#### Scenario: A pass repeated before the queue drains

- **WHEN** a backfill runs while example-fetch tasks are still queued
- **THEN** the pairs those tasks carry are named again and no second task is written for them

#### Scenario: Two askers, one pair

- **WHEN** a backfill queues the fetch for a pair
- **AND** the reader adds that same entry to that same collection by hand
- **THEN** the queue holds one task for the pair

#### Scenario: A fetch that was dead-lettered

- **WHEN** an example-fetch task has been dead-lettered for a pair
- **AND** a backfill runs
- **THEN** the pair counts as missing and a task is queued for it
- **AND** the dead-lettered task is still there to read

### Requirement: A throttled answer pauses the whole example-fetch queue

When the examples endpoint answers that the device is throttled and names a Retry-After delay, the
task queue SHALL start no further example fetch until that delay has passed. Fetches already in
flight SHALL be allowed to finish. The refused fetch SHALL be queued again for the end of the delay;
the other queued fetches SHALL keep their schedule.

A trigger that arrives before the delay has passed — a newly queued fetch, a resume, a flush — SHALL
NOT start a fetch. When the delay has passed, the queue SHALL resume on its own and SHALL drain what
is due.

#### Scenario: The endpoint throttles a queue of many fetches

- **WHEN** ten fetches are due and the first answer is a throttle with a Retry-After delay
- **THEN** only the fetches already in flight are sent
- **AND** no further fetch is sent until the delay has passed

#### Scenario: A new fetch is queued during the pause

- **WHEN** a fetch is queued while the queue is paused by a throttle
- **THEN** it is not sent before the Retry-After delay has passed

#### Scenario: The delay passes

- **WHEN** the Retry-After delay has passed
- **THEN** the queue resumes without any further trigger and sends every due fetch, the refused one
  included

### Requirement: A fetch whose pair is answered sends no request

An example-fetch task SHALL NOT run before the backfill may count (Requirement: A start queues the
example fetches the device already owes). It SHALL then ask whether its pair is answered, by the
rule the backfill counts by: `user-db` holds an example of the entry that a read in the pair's
collection sees (`specs/examples-schema/spec.md`). When it holds one — it arrived by replication,
was moved from `device-db`, or was written by another tab after the task was queued — the task SHALL
complete without sending an example request and SHALL write nothing. The task queue itself is not
held: other tasks run once memory is loaded (`specs/task-runner/spec.md`).

#### Scenario: The example arrived after the task was queued

- **WHEN** an example-fetch task is queued for a pair
- **AND** a replication pass then brings an example of that pair
- **AND** the task runs
- **THEN** no example request is sent and the task completes

#### Scenario: A fetch before the device is ready

- **WHEN** an example-fetch task is due while the examples kept on the device are still moving
- **THEN** it sends nothing until the move is done, and then only when its pair is still unanswered

#### Scenario: An example of another collection

- **WHEN** an example-fetch task for entry W in collection T runs
- **AND** `user-db` holds an example of W in another collection only
- **THEN** the example request is sent
