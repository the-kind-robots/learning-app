## MODIFIED Requirements

### Requirement: A replication pass queues the example fetches the device owes

After a completed replication pass the device SHALL count its debt over what that pass brought home,
and SHALL NOT read the rest of the vocabulary. Examples live in `device-db` and never replicate, so
a document that arrived by replication can leave a pair without an example here.

Debt is counted over vocabulary entries, and a phrase is one: words and phrases are the same
document type and ask for an example alike (`specs/examples/spec.md`). Nothing in this requirement
reads an entry's kind.

A pass brings two kinds of debt, and both SHALL be counted:

- an entry that arrived — it needs an example for every named collection holding it, or, when it
  belongs to none, one example of its own;
- a collection that arrived — every entry it names may have been on this device all along and been
  put into that collection on another device, and no example here carries that pair.

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

### Requirement: A start queues the example fetches the device already owes

On start the device SHALL count its debt over every vocabulary entry it holds — words and phrases
alike — and queue what it owes, bounded by the cap. This is the one full reading: it closes what
earlier runs left, whatever the reason — the cap, a device that was offline, a failure.

#### Scenario: A device starts holding entries without examples

- **WHEN** the application starts
- **THEN** every entry it holds is considered, and an example-fetch task is queued for each owed
  pair up to the cap

#### Scenario: A device starts owing nothing

- **WHEN** the application starts and every pair already has its example or its queued task
- **THEN** no task is queued
