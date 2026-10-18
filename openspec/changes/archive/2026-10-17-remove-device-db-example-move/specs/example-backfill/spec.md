## MODIFIED Requirements

### Requirement: The device asks for the examples memory is missing

The device SHALL ask, eventually, for an example for every pair memory has no example for, and for
nothing else, in any order and at any pace. It SHALL decide whether a pair has an example from memory
as it stands when it looks, and SHALL keep no stored list of pairs: a pair answered since — by
replication, by another tab — is no longer asked for, and a word or
collection deleted since asks for nothing. It SHALL read memory in chunks, each well under a frame.

The pairs follow the lookup rules in `specs/examples-schema/spec.md`:

- for every named collection and every entry in it, when no example carries that entry and that
  collection — a named collection's lookup is strict by `collection-id`, so an example generated
  elsewhere does not answer it;
- for every entry that belongs to no collection, when no example carries that entry at all — the
  main card is a union view, so any example for the entry answers it.

What is missing is counted over vocabulary entries, and a phrase is one: words and phrases are the
same document type and ask for an example alike (`specs/examples/spec.md`). Nothing in this
requirement reads an entry's kind.

The device SHALL look for a missing pair after each request and, while it has nothing to ask, when
memory's words or collections change. Adding a word or a phrase, changing a translation and a word
arriving by replication all change them, so the pair is asked for at once. A review or an example
changes neither and SHALL NOT wake it. There is no periodic look.

#### Scenario: A word arrived by replication without its example

- **WHEN** memory takes a word from a replication pass
- **AND** memory holds no example of it
- **THEN** an example request is sent for it at once

#### Scenario: A word arrived by replication with its example

- **WHEN** memory takes a word and the example another device fetched for it
- **THEN** no example request is sent for it

#### Scenario: An example arrives before the request is sent

- **WHEN** a pair is missing its example
- **AND** memory takes an example of that pair before a request for it is sent
- **THEN** no request is sent for it

#### Scenario: A word added on this device

- **WHEN** the learner adds a word that has no example in the active collection
- **THEN** an example request is sent for that word and that collection at once
- **AND** no request is sent for the word outside every collection, although the word was written
  before its place in the collection

#### Scenario: A lesson answer

- **WHEN** the learner answers a card while nothing is missing
- **THEN** the device does not look for a missing pair

#### Scenario: A translation changed

- **WHEN** the learner changes the translation of a word whose pair failed
- **THEN** the pair is asked for again at once

#### Scenario: A phrase without its example

- **WHEN** memory holds a phrase with no example
- **THEN** an example request is sent for it, carrying the phrase's own Russian translations

#### Scenario: A word in a collection whose example came from another collection

- **WHEN** a word belongs to collection T and its only example carries a different collection, or none
- **THEN** an example request is sent for the word in T, with T's name

#### Scenario: A word in no collection with any example

- **WHEN** a word belongs to no collection and an example exists for it under some collection
- **THEN** no request is sent for it — the main card's union lookup already answers

#### Scenario: A deleted collection

- **WHEN** a collection naming entry W is deleted before a request for W in it is sent
- **THEN** no request is sent for W in that collection

#### Scenario: Nothing to ask

- **WHEN** every pair has its example
- **THEN** no request is sent, and the device looks again only when memory's words or collections change

### Requirement: Nothing is asked for before the device knows what the account holds

The device SHALL send no example request before memory is loaded and the first replication pass of
this session has completed. So an example the account already holds on another device answers its
pair before anything is asked. When the first pass has not completed 60 s after the
device began waiting, the device SHALL stop waiting for it.

A device without an account SHALL send no example request: the endpoint answers only a session of
an account (`specs/examples/spec.md`).

#### Scenario: A device with an account starts

- **WHEN** a device with an account starts holding entries whose examples the account holds on
  another device
- **THEN** no example request is sent before the first pass of the session has completed
- **AND** no request is sent for those entries once it has

#### Scenario: A device starts with examples kept on the device

- **WHEN** the application starts on a device whose `device-db` holds the example of a word
- **THEN** that example is not read, and a request is sent for that word's pair

#### Scenario: A device without an account

- **WHEN** a device without an account holds entries with no example
- **THEN** no example request is sent

#### Scenario: The first pass does not complete

- **WHEN** a device with an account starts and its first pass has not completed after 60 s
- **THEN** the device asks for what memory is missing without waiting further
