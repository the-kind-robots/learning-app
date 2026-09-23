## ADDED Requirements

### Requirement: A generated example is cached under the request that produced it

The server SHALL keep every valid generated example in its own database, under a key derived from
the request that produced it: the German word, the confirmed Russian glosses, and the collection
context. The key SHALL be built from a normalized form of those three:

- the word is trimmed and its case kept — German case carries meaning, and `Essen` is not `essen`;
- the glosses are trimmed, blanks dropped, duplicates dropped, and sorted, so the same set of glosses
  in a different order is the same request;
- the context is trimmed, and absent when blank.

The stored row SHALL carry both the key's digest and the normalized fields it was built from, so the
accumulated cache can be read back by word, gloss or context without recomputing digests.

#### Scenario: A valid example is stored

- **WHEN** a request generates a valid example
- **THEN** the example is stored under the digest of its normalized request
- **AND** the row also carries the normalized word, glosses and context

#### Scenario: The same glosses in another order are the same request

- **WHEN** one request names the glosses `собака, пёс` and another names `пёс, собака`
- **THEN** both resolve to the same cache key

#### Scenario: Case in the word makes a different request

- **WHEN** one request names the word `Essen` and another names `essen`
- **THEN** they resolve to different cache keys

#### Scenario: Context is part of the key

- **WHEN** two requests name the same word and glosses, one with a collection context and one without
- **THEN** they resolve to different cache keys

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
