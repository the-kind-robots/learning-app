## MODIFIED Requirements

### Requirement: A generated example is cached under its subject

The server SHALL keep every valid generated example in its own database, under a key derived from
its subject — the German word, the confirmed glosses, the collection context and the language the
example is in (`specs/examples/spec.md`, Requirement: An example is generated in the learner's
language) — together with what the dictionary says about the word and the operator's cache version.
The key SHALL be built from a normalized form of the subject:

- the word is trimmed and its case kept — German case carries meaning, and `Essen` is not `essen`;
- the glosses are trimmed, blanks dropped, duplicates dropped, and sorted, so the same set of glosses
  in a different order is the same subject;
- the context is trimmed, and absent when blank;
- the language is `ru` when the request names none.

What the dictionary says SHALL be the word's part of speech and level, or that it has no entry for
the word. It goes into the prompt, so it belongs in the key: the word arriving in the dictionary is
a miss. The system prompt and the configured models SHALL NOT be part of the key: an example fixes
the meaning it was made for, and an edited prompt or another model keeps every stored example
reachable. The cache version is the environment variable `EXAMPLE_CACHE_VERSION`; it SHALL be
absent in the normal state, and setting or changing it SHALL make every earlier row a miss, which is
the deliberate full regeneration. There is no other invalidation.

The same normalized glosses SHALL be what the generation is asked with, so a subject cannot be
keyed on one gloss list and generated from another. Likewise the dictionary SHALL be read once per
request, and that one reading SHALL be what both the key and the prompt are built from. When the
dictionary cannot be read, no example SHALL be generated and nothing SHALL be stored: without the
reading there is neither a prompt nor a key to pay for.

The key's material SHALL NOT depend on how Clojure prints: under a bound `*print-length*` or
`*print-level*` two different subjects would compose the same key.

The stored row SHALL carry both the key's digest and the normalized fields it was built from, so the
accumulated cache can be read back by word, gloss, context or language without recomputing digests.
The glosses SHALL be stored as a JSON array, since a gloss may itself contain a comma. Rows written
before this keep their glosses joined by `", "`: the column is for reading and is no part of the
key. Rows written before the language was part of the subject are Russian.

#### Scenario: A valid example is stored

- **WHEN** a request generates a valid example
- **THEN** the example is stored under the digest of its normalized subject and dictionary reading
- **AND** the row also carries the normalized word, glosses, context and language, the glosses as a JSON array

#### Scenario: The same glosses in another order are the same subject

- **WHEN** one request names the glosses `собака, пёс` and another names `пёс, собака`
- **THEN** both resolve to the same cache key

#### Scenario: The glosses that key the subject are the glosses it is generated from

- **WHEN** a request names the gloss `" собака "` and another names `"собака"`
- **THEN** both resolve to the same cache key
- **AND** both are generated from the gloss `собака`

#### Scenario: Case in the word makes a different subject

- **WHEN** one request names the word `Essen` and another names `essen`
- **THEN** they resolve to different cache keys

#### Scenario: Context is part of the key

- **WHEN** two requests name the same word and glosses, one with a collection context and one without
- **THEN** they resolve to different cache keys

#### Scenario: The language is part of the key

- **WHEN** two requests name the same word, glosses and context, one with `language=en` and one with `language=ru`
- **THEN** they resolve to different cache keys
- **AND** a request that names no language resolves to the key of `language=ru`

#### Scenario: The prompt is edited

- **WHEN** the system prompt changes
- **AND** a request repeats a subject whose example was generated before
- **THEN** the stored example is served and the provider is not called

#### Scenario: Another model is configured

- **WHEN** the configured model changes
- **AND** a request repeats a subject whose example was generated before
- **THEN** the stored example is served and the provider is not called

#### Scenario: The cache version changes

- **WHEN** `EXAMPLE_CACHE_VERSION` is set or changed
- **AND** a request repeats a subject whose example was generated before
- **THEN** it is a miss, and a new example is generated and stored

#### Scenario: The word arrives in the dictionary

- **WHEN** an example was generated while the dictionary had no entry for the word
- **AND** a later request repeats that subject while the dictionary knows the word
- **THEN** it is a miss, and a new example is generated and stored

#### Scenario: The dictionary is read once

- **WHEN** a request misses the cache and generates
- **THEN** the dictionary is read once for it
- **AND** the prompt carries what that read returned

#### Scenario: The dictionary has no entry for the word

- **WHEN** an example is generated for a word the dictionary has no entry for
- **THEN** it is stored like any other, under a key that says the dictionary has no entry for it
- **AND** a later request for the same subject is served from the cache

#### Scenario: The dictionary is away

- **WHEN** a request misses the cache and the dictionary cannot be read, or does not answer
- **THEN** the provider is not called and nothing is stored

### Requirement: Earlier rows are re-keyed at startup

Once it is listening, the server SHALL, in the background, give every row stored under an earlier key the key the current rule
computes from the row's own word, glosses, context and language and the dictionary's present reading of the
word. When two rows land on one key, the one stored first SHALL be kept and the other removed. A row
whose key was already computed this way SHALL NOT be computed again. When the dictionary cannot be
read, the step SHALL stop, leave the remaining rows as they are, and not affect the server; the next
start continues it.

#### Scenario: Rows stored before the key changed

- **WHEN** the server has started with rows stored under the earlier key and the re-key has finished
- **THEN** a request for the subject of such a row is served from it, and the provider is not called

#### Scenario: Rows stored before the language was in the key

- **WHEN** the server has started with rows stored before the key carried the language, and the re-key has finished
- **THEN** a Russian request for the subject of such a row is served from it, and the provider is not called

#### Scenario: A request arrives before its row is re-keyed

- **WHEN** a request repeats the subject of a row the re-key has not reached yet
- **THEN** it generates and stores as on a miss
- **AND** the re-key later keeps the row stored first

#### Scenario: Two rows land on one key

- **WHEN** two earlier rows, stored under different prompts, have the same subject and dictionary reading
- **THEN** one row remains, the one stored first

#### Scenario: A second start

- **WHEN** the server starts again after the rows were re-keyed
- **THEN** the dictionary is not read for them again

#### Scenario: The dictionary is away at startup

- **WHEN** the server starts and the dictionary cannot be read
- **THEN** the server serves, and the rows still waiting are re-keyed at a later start
