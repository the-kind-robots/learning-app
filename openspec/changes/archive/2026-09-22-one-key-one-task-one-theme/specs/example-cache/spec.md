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

## REMOVED Requirements

### Requirement: An example generated without the dictionary is not cached

**Reason**: It patched a key that did not cover the dictionary metadata, at the price of the saving
being lost exactly when it is worth most — a dictionary that is away otherwise makes every device
pay the provider for the same sentence. The metadata is part of the key now, so a degraded answer
is a legitimate answer to a question that says so.

**Migration**: None. Rows keyed under the old generation are no longer found, and the first request
for each question stores it again.
