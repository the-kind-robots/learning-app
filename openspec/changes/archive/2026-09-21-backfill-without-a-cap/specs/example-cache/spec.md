## MODIFIED Requirements

### Requirement: A generated example is cached under the request that produced it

The server SHALL keep every valid generated example in its own database, under a key derived from
the request that produced it — the German word, the confirmed Russian glosses, and the collection
context — together with the generation that would answer it. The key SHALL be built from a
normalized form of the request:

- the word is trimmed and its case kept — German case carries meaning, and `Essen` is not `essen`;
- the glosses are trimmed, blanks dropped, duplicates dropped, and sorted, so the same set of glosses
  in a different order is the same request;
- the context is trimmed, and absent when blank.

The generation SHALL be the system prompt the sentence would be asked with and the models configured
to answer it. A sentence depends on those as much as on the request, so an edited prompt or another
model is a miss and the rows answered under the old generation are simply no longer found. There is
no other invalidation.

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

## ADDED Requirements

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

### Requirement: An example generated without the dictionary is not cached

The server SHALL NOT store an example generated while the dictionary held no metadata for the
target. The prompt then carries no part of speech and the default level, so the sentence is the best
that could be had for the caller who asked — and not the answer every later account should be
served.

#### Scenario: The dictionary lookup answers nothing

- **WHEN** an example is generated for a word the dictionary has no entry for, or while the
  dictionary is unavailable
- **THEN** the example is served to the caller
- **AND** nothing is written to the cache
- **AND** the next request for the same question generates again
