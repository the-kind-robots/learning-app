## ADDED Requirements

### Requirement: The device asks for the examples memory is missing

The device SHALL ask, eventually, for an example for every pair memory has no example for, and for
nothing else, in any order and at any pace. It SHALL decide whether a pair has an example from memory
as it stands when it looks, and SHALL keep no stored list of pairs: a pair answered since — by
replication, by the move from `device-db`, by another tab — is no longer asked for, and a word or
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

The device SHALL look for a missing pair after each request, when it is woken, and every 30 s while
it has nothing to ask. Adding a word or a phrase and changing a word's translation SHALL wake it, so
the pair is asked for at once. A word that arrives by replication is asked for at the next look.

#### Scenario: A word arrived by replication without its example

- **WHEN** memory takes a word from a replication pass
- **AND** memory holds no example of it
- **THEN** an example request is sent for it within 30 s

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
- **THEN** no request is sent, and the device looks again every 30 s

### Requirement: Nothing is asked for before the device knows what the account holds

The device SHALL send no example request before memory is loaded, the examples kept in `device-db`
have moved to `user-db` (`specs/data-model/spec.md`), and the first replication pass of this session
has completed. So an example the move brings, or one the account already holds on another device,
answers its pair before anything is asked. When the first pass has not completed 60 s after the
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
- **THEN** no request is sent for that word's pair

#### Scenario: A device without an account

- **WHEN** a device without an account holds entries with no example
- **THEN** no example request is sent

#### Scenario: The first pass does not complete

- **WHEN** a device with an account starts and its first pass has not completed after 60 s
- **THEN** the device asks for what memory is missing without waiting further

### Requirement: Only a visible, online tab asks

A tab SHALL send example requests only while it is visible and the device is online. A tab that
becomes hidden, or whose device goes offline, SHALL abort its request in flight, marking nothing. A tab
that could not send SHALL look again once it is visible and the device is online; with nothing missing
it looks again only when memory's words or collections change (Requirement: The device asks for the
examples memory is missing). Two visible tabs MAY both send requests: the same example stored
twice is one document (`specs/examples-schema/spec.md`).

#### Scenario: A hidden tab

- **WHEN** a tab of the app is hidden and pairs are missing
- **THEN** it sends no example request

#### Scenario: The tab is hidden mid-request

- **WHEN** the tab becomes hidden while a request is in flight
- **THEN** the request is aborted and its pair stays eligible

#### Scenario: The tab is shown again

- **WHEN** a hidden tab becomes visible and pairs are missing
- **THEN** it sends a request

#### Scenario: Offline

- **WHEN** the device is offline and pairs are missing
- **THEN** no request is sent until the `online` event

#### Scenario: The device goes offline mid-request

- **WHEN** the device goes offline while a request is in flight
- **THEN** the request is aborted and its pair stays eligible

### Requirement: Example requests are paced

A tab SHALL have at most one example request in flight and SHALL start the next no sooner than 2 s
after the previous one was answered. A request not answered within 110 s SHALL be aborted, and its
pair SHALL be treated as when the generator is unavailable (Requirement: Each failure has one
effect, and nothing is retried forever). The proxy in front of the backend gives up sooner, so a
request is not aborted while the backend still generates.

#### Scenario: Many missing pairs

- **WHEN** ten pairs are missing and every request takes 5 s to answer
- **THEN** never more than one request is in flight
- **AND** no request starts less than 2 s after the previous one was answered

#### Scenario: A request that hangs

- **WHEN** a request is not answered within 110 s
- **THEN** it is aborted, and the device pauses as for an unavailable generator
- **AND** the pair is asked for again after the pause

### Requirement: Each failure has one effect, and nothing is retried forever

Each response to an example request SHALL have the effect its failure kind
(`specs/example-fetch-error-clarity/spec.md`) gives it, and no other:

| Response | Effect |
|---|---|
| a valid example | saved at once; the run of outages is over |
| a subject that cannot be sent, an invalid body, or a refusal of the request itself (400, 404, 422, or any 4xx not named below) | the subject is not asked for again; nothing pauses; the run of outages is over |
| an authentication refusal (401, 403) | the tab sends nothing until the page is reloaded |
| a throttle (429) | the tab pauses; the pair is asked for again after the pause |
| an unavailable generator (5xx, 503 among them; 408 and 425 count the same) or a timeout | the tab pauses; the pair is asked for again after the pause |
| a network failure while online | the tab pauses; the pair is asked for again after the pause |
| a request aborted because the tab was hidden | nothing |

A throttle, an unavailable generator, a timeout and a network failure are outages: they say
nothing about the pair. The k-th outage in a row SHALL pause the tab for
5 s × 2^(k−1), at most 5 min, or for the response's `Retry-After` when that is longer; no pause SHALL
be longer than an hour. No number of outages in a row SHALL stop the tab. When the pause ends, the tab SHALL look again without any other trigger, and waking the tab
SHALL NOT end a pause.

"Not asked for again" SHALL last for the life of the page and SHALL apply to the subject the request
asked about — the entry's text, its Russian translations and the collection's name — so editing the
entry or renaming the collection, even while the request is in flight, makes the pair eligible
again, and so does a reload. Any response that is not an outage — a valid example, or a failure of the pair — SHALL end the run of outages. Showing the tab again
SHALL NOT end a stop, a run of outages or a pause, and SHALL NOT make a subject that failed
eligible again.

#### Scenario: A throttle

- **WHEN** a request is answered 429 with `Retry-After: 30`
- **THEN** no request is sent for 30 s
- **AND** after that the tab sends again on its own, the throttled pair included

#### Scenario: The generator is unavailable

- **WHEN** a request is answered 503 with `Retry-After: 30`
- **THEN** no request is sent for 30 s
- **AND** after that the same pair is asked for again

#### Scenario: A pair the generator cannot answer

- **WHEN** a request is answered 422
- **THEN** that subject is not asked for again in this page's life
- **AND** the next pair is asked for without a pause

#### Scenario: An expired session

- **WHEN** a request is answered 401
- **THEN** the tab sends no further request until the page is reloaded

#### Scenario: Many outages in a row

- **WHEN** more than six requests in a row end in outages
- **THEN** each pause is at most 5 min
- **AND** after each pause the tab asks again, without a reload

#### Scenario: A pair failure ends the run of outages

- **WHEN** outages alternate with answers of 422
- **THEN** every pause is the first back-off step, 5 s

#### Scenario: A pair that failed, edited

- **WHEN** a pair was not asked for again after an invalid response
- **AND** the learner edits the entry's translation
- **THEN** the pair is asked for again

#### Scenario: A word added during a pause

- **WHEN** the learner adds a word while the tab is paused
- **THEN** its pair is asked for when the pause ends

### Requirement: A received example is saved at once

A tab SHALL save each example it receives in its own write, before it sends the next request. A save
SHALL keep an example `user-db` holds already as it is (`specs/examples-schema/spec.md`). A save that
fails SHALL be logged, and its subject SHALL NOT be asked for again in the page's life.

#### Scenario: An example arrives

- **WHEN** a request is answered with an example
- **THEN** the example is saved before the next request is sent
