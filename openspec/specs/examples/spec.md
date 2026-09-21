# examples Specification

## Purpose
Define how example sentences are generated and stored for vocabulary words, including task creation for fetching examples.
## Requirements
### Requirement: Example documents are stored
The system SHALL store example documents defined in `specs/data-model/spec.md`. Per-collection scoping rules are defined in `specs/examples-schema/spec.md`.

#### Scenario: Store example document
- **WHEN** an example is fetched for a word
- **THEN** an example document is created matching the example document shape in `specs/data-model/spec.md`

### Requirement: Generating an example needs an authenticated session

The example generation endpoint SHALL resolve the bearer token from the session cookie to an account
before it does anything else, and SHALL answer `401` when no account is resolved. A request that does
not authenticate SHALL NOT reach the generation provider, and SHALL NOT be answered with a validation
error either — the check comes before argument parsing, so an anonymous caller learns nothing about
the endpoint's parameters.

Every request that generates an example costs money at an upstream provider, so an unauthenticated
request is not merely unauthorized, it is billable.

#### Scenario: No session cookie

- **WHEN** a request arrives at the example endpoint carrying no session cookie
- **THEN** the response is `401`
- **AND** the generation provider is not called

#### Scenario: Session cookie that resolves to no account

- **WHEN** a request carries a token that matches no account
- **THEN** the response is `401`
- **AND** the generation provider is not called

#### Scenario: Missing word from an unauthenticated caller

- **WHEN** a request without a valid session omits the word parameter
- **THEN** the response is `401`, not the `400` an authenticated caller would get

#### Scenario: Authenticated request

- **WHEN** a request carries a session cookie whose token resolves to an account, and names a word
- **THEN** the request is served exactly as before: a generated example, or the endpoint's own error
  status when generation fails

### Requirement: The client sends its session with every example request

The client SHALL send the session cookie with each example request. The request SHALL declare its
credentials mode explicitly rather than depend on the browser default for same-origin URLs, because
the default stops applying the moment the request URL becomes cross-origin, and the resulting failure
is silent — examples stop arriving and nothing reports why.

The client SHALL NOT run example fetch tasks before the session cookie has been written. A queued
task from an earlier session would otherwise race the component that writes the cookie, and the
outcome of that race decides whether the first example of the boot is answered or refused.

#### Scenario: Fetching an example

- **WHEN** the client requests an example for a word
- **THEN** the request carries the session cookie
- **AND** the endpoint authenticates it as the account that owns the session

#### Scenario: A queued fetch task at boot

- **WHEN** the app starts with an example fetch task already in the queue
- **THEN** the task runner does not start until the session cookie has been written
- **AND** the request it issues authenticates like any other

### Requirement: Example fetch tasks are created on vocabulary entry creation
The system SHALL create an example-fetch task whenever a vocabulary entry is created, whether it is a word or a phrase, carrying the active collection context (see `specs/examples-schema/spec.md`).

#### Scenario: Word creation triggers example-fetch task
- **WHEN** a word is added
- **THEN** an example-fetch task document is persisted for that word via the examples module

#### Scenario: Phrase creation triggers example-fetch task
- **WHEN** a phrase is added
- **THEN** an example-fetch task document is persisted for that phrase via the examples module, with the same payload shape a word's task has

### Requirement: A generated example for a phrase carries the whole construction
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction. It MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. An example whose sentence does not carry the whole construction SHALL be rejected as invalid and regenerated.

`structure` SHALL annotate the sentence word by word under the same rules whatever the target is, and SHALL NOT record which words belonged to the construction. Presence of a multi-word target is therefore checked against the sentence: every word of the target SHALL be present, matching either a word of the sentence or the `dictionaryForm` of some `structure` item, which is what carries inflection. A single-lemma target SHALL keep being checked against `structure`, by the `dictionaryForm` that names it.

#### Scenario: Inflected phrase inside a sentence
- **WHEN** an example is generated for the phrase "den Kopf verlieren"
- **THEN** a sentence such as "Er verliert den Kopf." is accepted
- **AND** "den" and "Kopf" are found in the sentence, and "verlieren" through the `structure` item whose `dictionaryForm` names it

#### Scenario: Phrase split by German word order
- **WHEN** an example is generated for the phrase "auf jeden Fall"
- **THEN** a sentence such as "Ich komme auf jeden Fall mit." is accepted although the construction sits inside the clause
- **AND** `structure` annotates the sentence's words under the ordinary rules, so "Fall" carries its own lemma and "auf" and "jeden" are left out as a preposition and a determiner

#### Scenario: A word the sentence says twice
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit"
- **THEN** the example is accepted although "Zeit" occurs twice
- **AND** each occurrence is its own `structure` item, annotated as the noun it is, with its own `wordIndex`

#### Scenario: Phrase that is already a sentence
- **WHEN** an example is generated for a phrase that is itself a complete sentence
- **THEN** the generated sentence extends or embeds it into a turn rather than repeating it unchanged

#### Scenario: Construction missing from the output
- **WHEN** the generated sentence carries only some of the construction's words
- **THEN** the example is rejected as invalid and generation is retried, and the retry is told the same rule in the same words the prompt states it in
