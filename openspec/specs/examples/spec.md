# examples Specification

## Purpose
Define how example sentences are generated and stored for vocabulary words, and how a request for one authenticates.

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

### Requirement: A generated example for a phrase carries the whole construction
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction. It MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. An example whose sentence does not carry the whole construction SHALL be rejected as invalid and regenerated.

A placeholder of the phrase — `etwas`, `jemand`, `jemanden`, `jemandem`, `jemandes`, `sich`, the possessives `seine`, `seinen`, `seinem`, `seiner`, `seines`, the abbreviations of these (`etw.`, `jdn`, `jdm`, `jmdm` …) and `einer Sache` — is a slot, not a word: the sentence SHALL NOT be required to say it, and MAY fill it with any noun phrase or pronoun that fits, `sich` with any reflexive pronoun. The articles a phrase lists SHALL NOT be required either: the sentence puts the determiner its noun needs. Every other word of the phrase SHALL be required.

`structure` SHALL annotate the sentence word by word under the same rules whatever the target is, and SHALL NOT record which words belonged to the construction. Presence of a multi-word target is therefore checked against the sentence: every required word SHALL be present, matching either a word of the sentence or the `dictionaryForm` of some `structure` item (or a word of it), which is what carries inflection. A single-lemma target SHALL be present when `structure` names it by a `dictionaryForm`, when the sentence says it in the form it is written in, or when the sentence or some `dictionaryForm` is any form of the same dictionary lemma — a learner who saved `explodiert` is served `explodierte`. Forms belong to the lemmas the dictionary's form index lists them under; a target with an article belongs only to the lemma written so. Every required word of a multi-word target SHALL be present by the same rule. A target the dictionary does not know SHALL be present only by the earlier rules: named by a `dictionaryForm` or said in the form it is written in.

An imperfect `structure` SHALL NOT cost the example. An item SHALL be placed on the first matching word of the sentence after the previous item's, else on the first matching word before it; an item of two words SHALL count as one item per word; an item that matches no word of the sentence, or whose translation is not Russian, SHALL be left out of the stored `structure`. The stored `structure` SHALL be ordered by the position of the words in the sentence. An example none of whose items can be placed SHALL be rejected as invalid and regenerated.

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

#### Scenario: A placeholder filled with an object
- **WHEN** an example is generated for the phrase "Anspruch auf etwas haben"
- **THEN** "Jeder Arbeitnehmer hat einen Anspruch auf Urlaub." is accepted although the sentence does not say "etwas"

#### Scenario: A reflexive placeholder
- **WHEN** an example is generated for the phrase "sich bei jemandem vorstellen"
- **THEN** a sentence with "mich", "dich", "uns" or any other reflexive pronoun in place of "sich" is accepted

#### Scenario: Another article than the phrase lists
- **WHEN** an example is generated for the phrase "ein Versprechen brechen"
- **THEN** "Ich werde mein Versprechen nicht brechen." is accepted

#### Scenario: A word that is no placeholder is missing
- **WHEN** an example is generated for the phrase "Bezug auf etwas nehmen" and the sentence says neither "Bezug" nor "nehmen"
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: A single word in the form it is written in
- **WHEN** an example is generated for the word "verzweifelt" and the sentence is "Er verzweifelt an der Aufgabe." with its verb annotated under the lemma "verzweifeln"
- **THEN** the example is accepted

#### Scenario: A structure item that cannot be placed
- **WHEN** a generated example annotates a word out of order, spells one as its lemma, or glosses one in English
- **THEN** the example is accepted and the stored `structure` holds the items that could be placed, in sentence order

#### Scenario: A word saved in an inflected form
- **WHEN** an example is generated for the word "explodiert" and the sentence is "Die Bombe explodierte laut im Hof."
- **THEN** the example is accepted although the sentence says "explodierte", another form of the same lemma, and `structure` need not name it

#### Scenario: A form shared by two lemmas
- **WHEN** an example is generated for the word "federn" and the sentence says "Die Feder liegt auf dem Tisch." or "Die Federn des Vogels sind weich."
- **THEN** the example is accepted, as the dictionary lists "Federn" under the verb and under "die Feder"

#### Scenario: A participle that is also a verb form
- **WHEN** an example is generated for the word "erhöht" and the sentence is "Der Chef erhöhte den Preis stark."
- **THEN** the example is accepted

#### Scenario: No form of the lemma
- **WHEN** an example is generated for the word "erhöht" and the sentence has no form of "erhöhen" or "erhöht"
- **THEN** the example is rejected as invalid and regenerated

#### Scenario: A word the dictionary does not know
- **WHEN** an example is generated for a word absent from the dictionary
- **THEN** it is accepted only when the sentence says the word as written or `structure` names it by a `dictionaryForm`

### Requirement: A generation request carries the word's part of speech and CEFR level

Before it builds a generation request the system SHALL look the target up in the dictionary, and
SHALL carry the found entry's part of speech and CEFR level in that request. A target the dictionary
does not know SHALL be sent without a part of speech and at the default CEFR level.

The dictionary is the SQLite dictionary built for the client; the system SHALL read it directly and
SHALL NOT depend on a separate dictionary database.

The lookup SHALL find a target of several words, spaces included, as it finds a single word.

The lookup SHALL answer from an index over the form it selects on, and SHALL NOT read the whole
dictionary to answer. The two outcomes the caller can observe — "the dictionary knows this word" and
"it does not" — SHALL NOT depend on how large the dictionary is.

A dictionary file that is missing or unreadable SHALL stop the system from starting, so a broken
dictionary is never mistaken for a word the dictionary does not know.

#### Scenario: A word the dictionary knows

- **WHEN** an example is generated for a word that has a dictionary entry with a part of speech and a
  CEFR level
- **THEN** the generation request carries that part of speech and that CEFR level

#### Scenario: A word the dictionary does not know

- **WHEN** an example is generated for a target with no dictionary entry
- **THEN** the generation request carries no part of speech and the default CEFR level
- **AND** generation proceeds

#### Scenario: A phrase without an article

- **WHEN** the dictionary is asked for a phrase of several words that has no article, such as "Bezug auf etwas nehmen"
- **THEN** the lookup answers without error, as for any word

#### Scenario: An article keeps the lemma distinct

- **WHEN** the dictionary is asked for "die Leiter"
- **THEN** only the entry written with that article is found, not "der Leiter"

#### Scenario: The lookup query is planned against an index

- **WHEN** the dictionary is asked for the entries of a normalized word
- **THEN** the query is answered from an index over the form, not by reading every row

#### Scenario: A dictionary loaded from scratch

- **WHEN** the system starts against a freshly built dictionary file
- **THEN** the lookup answers from it with no separate step, such as creating an index

#### Scenario: The dictionary file is missing

- **WHEN** the system starts and the dictionary file cannot be opened
- **THEN** it fails to start and says which file it could not open

### Requirement: Every example request carries the session, and none goes before it is written

The client SHALL send the session cookie with each example request. The request SHALL declare its
credentials mode explicitly rather than depend on the browser default for same-origin URLs, because
the default stops applying the moment the request URL becomes cross-origin, and the resulting failure
is silent — examples stop arriving and nothing reports why.

The client SHALL NOT send an example request before the session cookie of this start has been
written. A request sent earlier would race the component that writes the cookie, and the outcome of
that race decides whether the first example of the boot is answered or refused.

#### Scenario: Fetching an example

- **WHEN** the client requests an example for a word
- **THEN** the request carries the session cookie
- **AND** the endpoint authenticates it as the account that owns the session

#### Scenario: Missing examples at boot

- **WHEN** the app starts holding entries without examples
- **THEN** no example request is sent before the session cookie has been written
- **AND** the requests it then sends authenticate like any other

### Requirement: The example endpoint never answers with an exception message

The example endpoint SHALL answer `400` with a fixed message when `word` or `context` is given more than once, after the session check, and SHALL NOT call the generation provider. Any exception raised while answering a request SHALL be answered `500` with an empty body; the exception's own message SHALL NOT appear in any response.

#### Scenario: Repeated parameter, authenticated

- **WHEN** an authenticated request carries `context` twice
- **THEN** the response is `400` and its body does not contain an exception message
- **AND** the generation provider is not called

#### Scenario: Repeated parameter, no session

- **WHEN** a request without a valid session carries `context` twice
- **THEN** the response is `401`

#### Scenario: A handler throws

- **WHEN** a handler raises an exception
- **THEN** the response is `500` with an empty body

### Requirement: A picked part of speech decides the dictionary entry of the example
The example request of a word whose document carries `pos` SHALL carry it as the `pos` parameter, and a word without it SHALL send none. When the request has `pos`, the lookup SHALL choose among the dictionary entries of that part of speech only, the entry whose gloss matches first, and the generation request SHALL carry that entry's lemma and part of speech. When the dictionary has no entry of that part of speech for the word, the lookup SHALL choose as it does without `pos`. A request without `pos` SHALL be answered as before. When the lookup used the picked part of speech, a generated example SHALL be rejected as invalid and regenerated when its `structure` names the word only as a lemma of another part of speech (`der Rauch` for the adjective `rauch`); a bare `dictionaryForm` that is also a lemma of the picked part of speech SHALL pass. An adjective and a verb count as one for this check, so a participle annotated as its verb (`explodierte` as `explodieren`) is right for a picked adjective, and the reverse. A word whose every example is rejected SHALL stay without an example rather than get one about another word. Without a picked part of speech there SHALL be no such check. Examples of the same word under different parts of speech SHALL NOT share a cached example.

#### Scenario: The adjective, not the noun
- **WHEN** an example is requested for "rauch" with `pos` of the adjective, and the dictionary has the adjective "rauch" and the noun "der Rauch"
- **THEN** the generation request carries the adjective's lemma and part of speech

#### Scenario: A picked adjective gets no example about the noun
- **WHEN** an example is generated for "rauch" with `pos` of the adjective and its `structure` names the word as "der Rauch"
- **THEN** the example is rejected as invalid and generation is retried, and a word whose examples are all rejected stays without one
- **AND** an example whose `structure` names it as "rauch" is accepted

#### Scenario: No part of speech picked
- **WHEN** an example is requested for "rauch" without `pos`
- **THEN** the entry is chosen by gloss, otherwise the first entry, as before

#### Scenario: Separate cache rows
- **WHEN** examples are requested for "rauch" as an adjective and as a noun
- **THEN** each is generated and stored under its own key
