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
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction, and `structure` SHALL represent it as one item per sentence word it annotates — never one item spanning several words — each carrying the whole phrase as its `dictionaryForm` and the same Russian gloss the sentence was generated for, so every annotated word gets a `wordIndex` of its own. A generated item whose `usedForm` holds several words SHALL be unfolded into one item per word, each keeping the span's `dictionaryForm` and gloss and taking its own `wordIndex`, when and only when those words occur consecutively in the sentence from the position being matched; a span the sentence does not say consecutively SHALL be rejected. A function word of the construction that the inflected sentence does not carry MAY be absent from `structure`. The construction MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A word repeated inside the construction SHALL keep an item per occurrence: a repeat is legitimate when the repeated word belongs to the phrase target, and a repeated `{usedForm, dictionaryForm}` pair SHALL still be rejected otherwise, which is what keeps a separable verb from annotating the preposition that shares its prefix's spelling. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. A generated example that does not satisfy this SHALL be rejected as invalid and regenerated, as one missing its target lemma already is.

#### Scenario: Inflected phrase inside a sentence
- **WHEN** an example is generated for the phrase "den Kopf verlieren"
- **THEN** the sentence contains the construction inflected, such as "Er verliert den Kopf."
- **AND** each word of the construction the sentence carries — "den", "Kopf", "verliert" — is its own `structure` item with `dictionaryForm` "den Kopf verlieren" and the same Russian gloss, and none of them spans more than one word

#### Scenario: Phrase split by German word order
- **WHEN** an example is generated for the phrase "auf jeden Fall"
- **THEN** a sentence such as "Ich komme auf jeden Fall mit." is accepted although the construction sits inside the clause
- **AND** each word of the phrase has its own `structure` item, so each carries its own `wordIndex`

#### Scenario: A generated item spanning the construction
- **WHEN** generation for "auf jeden Fall" returns a single `structure` item whose `usedForm` is "auf jeden Fall" and the sentence says those three words consecutively
- **THEN** the item is unfolded into one item per word, each with `dictionaryForm` "auf jeden Fall", the same gloss, and its own `wordIndex`
- **AND** the same item is rejected when the sentence does not say those words consecutively

#### Scenario: A word repeated inside the construction
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit"
- **THEN** the example is accepted although "Zeit" occurs twice in the construction
- **AND** each occurrence has its own `structure` item and its own `wordIndex`

#### Scenario: A repeat that does not belong to the target is still rejected
- **WHEN** the target is the separable verb "aufpassen" and `structure` carries the sentence's preposition "auf" beside the detached prefix "auf", both with `dictionaryForm` "aufpassen"
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: Phrase that is already a sentence
- **WHEN** an example is generated for a phrase that is itself a complete sentence
- **THEN** the generated sentence extends or embeds it into a turn rather than repeating it unchanged

#### Scenario: Construction missing from the output
- **WHEN** the generated sentence omits part of the construction, or no `structure` item names the whole phrase as its `dictionaryForm`
- **THEN** the example is rejected as invalid and generation is retried, and the retry is told the same rule in the same words the prompt states it in

