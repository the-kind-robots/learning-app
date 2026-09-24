# example-cache Specification

## Purpose
Define the server-side cache of generated example sentences, so a request already answered is served again without paying the provider, and the cache never becomes a reason to refuse a request.

## Requirements

### Requirement: A generated example is cached under the request that produced it

The server SHALL keep every valid generated example in its own database, under a key derived from
the request that produced it — the German word, the confirmed Russian glosses, and the collection
context — together with the generation that would answer it. The key SHALL be built from a
normalized form of the request:

- the word is trimmed and its case kept — German case carries meaning, and `Essen` is not `essen`;
- the glosses are trimmed, blanks dropped, duplicates dropped, and sorted, so the same set of glosses
  in a different order is the same request;
- the context is trimmed, and absent when blank.

The generation SHALL be everything else the sentence depends on: the system prompt it would be asked
with, the models configured to answer it, and what the dictionary says about the word — its part of
speech and level, or their absence. All three go into the prompt, so all three belong in the key: an
edited prompt, another model, or the word arriving in the dictionary is a miss, and the rows answered
under the old generation are simply no longer found. There is no other invalidation.

The same normalized glosses SHALL be what the generation is asked with, so a request cannot be
keyed on one gloss list and generated from another.

The key's material SHALL NOT depend on how Clojure prints: under a bound `*print-length*` or
`*print-level*` two different requests would compose the same key.

The stored row SHALL carry both the key's digest and the normalized fields it was built from, so the
accumulated cache can be read back by word, gloss or context without recomputing digests.

#### Scenario: A valid example is stored

- **WHEN** a request generates a valid example
- **THEN** the example is stored under the digest of its normalized request and generation
- **AND** the row also carries the normalized word, glosses and context

#### Scenario: The same glosses in another order are the same request

- **WHEN** one request names the glosses `собака, пёс` and another names `пёс, собака`
- **THEN** both resolve to the same cache key

#### Scenario: The glosses that key the request are the glosses it is generated from

- **WHEN** a request names the gloss `" собака "` and another names `"собака"`
- **THEN** both resolve to the same cache key
- **AND** both are generated from the gloss `собака`

#### Scenario: Case in the word makes a different request

- **WHEN** one request names the word `Essen` and another names `essen`
- **THEN** they resolve to different cache keys

#### Scenario: Context is part of the key

- **WHEN** two requests name the same word and glosses, one with a collection context and one without
- **THEN** they resolve to different cache keys

#### Scenario: The prompt is edited

- **WHEN** the system prompt changes
- **AND** a request repeats a question that was answered before
- **THEN** it is a miss, and a new example is generated and stored

#### Scenario: Another model is configured

- **WHEN** the configured model changes
- **AND** a request repeats a question that was answered before
- **THEN** it is a miss, and a new example is generated and stored

#### Scenario: The word arrives in the dictionary

- **WHEN** an example was generated while the dictionary held nothing for the word
- **AND** a later request repeats that question while the dictionary answers for it
- **THEN** it is a miss, and a new example is generated and stored

#### Scenario: The dictionary is away

- **WHEN** an example is generated while the dictionary holds nothing for the word
- **THEN** it is stored like any other, under a key that says the dictionary answered nothing
- **AND** a later request for the same question while the dictionary is still away is served from
  the cache

### Requirement: A cached request is answered without reaching the provider

`GET /api/examples` SHALL look the request up in the cache before generating. On a hit it SHALL
answer `200` with the stored example and SHALL NOT call the generation provider. On a miss it SHALL
generate as before.

A request is authenticated before anything else, cache lookup included: a hit is cheap, not free of
the session requirement stated in `specs/examples/spec.md`.

#### Scenario: Second request for the same word

- **WHEN** an authenticated request repeats a word, glosses and context that were generated before
- **THEN** the response is `200` carrying the stored example
- **AND** the generation provider is not called

#### Scenario: First request for a word

- **WHEN** an authenticated request names a word, glosses and context with no stored example
- **THEN** the example is generated and the response is served from that generation

#### Scenario: A cache hit still needs a session

- **WHEN** an unauthenticated request repeats a word that is in the cache
- **THEN** the response is `401` and the stored example is not served

### Requirement: Only a valid example is cached

The server SHALL store an example only when it passes the same validity check that decides whether
the request is answered `200`. A failed generation, a provider error and a malformed example SHALL
leave the cache as it was, so a bad answer is not served to every later request for the same word.

#### Scenario: Generation fails

- **WHEN** generation returns an error or an example that fails validation
- **THEN** nothing is written to the cache
- **AND** a later request for the same word generates again

### Requirement: The cache is shared and has no expiry

The cache SHALL be shared across accounts and SHALL NOT expire entries. A generated sentence is not
account data — reuse across devices and accounts is the saving the cache exists for. The collection
name participates as part of the key and in no other way.

The server SHALL NOT invalidate entries when a word's translations change: a changed gloss set is a
different key, so the next request is a miss and generates afresh, and the older entry stays valid
for anyone still asking the older question.

#### Scenario: Another account asks the same question

- **WHEN** one account has generated an example for a word, glosses and context
- **AND** another account requests the same word, glosses and context
- **THEN** the stored example is served, and the provider is not called

#### Scenario: A gloss changes

- **WHEN** a request repeats a word with a gloss set that differs from an earlier one
- **THEN** it is a miss and a new example is generated and stored
- **AND** the earlier entry is left in place

### Requirement: The cache never refuses a request

A failure to read or write the cache SHALL NOT change what the endpoint answers. The table is an
optimization over a provider that is still there: a read that fails is a miss, and a write that
fails is dropped — the answer it would have saved has already been generated and is about to be
served.

#### Scenario: The cache cannot be read

- **WHEN** reading the cache fails
- **THEN** the request generates as it would on a miss, and is answered

#### Scenario: The cache cannot be written

- **WHEN** storing a generated example fails
- **THEN** the request is still answered with that example
