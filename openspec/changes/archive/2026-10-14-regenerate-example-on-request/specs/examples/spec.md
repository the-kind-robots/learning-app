## ADDED Requirements

### Requirement: A regeneration request answers a different example

`POST /api/examples` with a JSON body `{word, translations, context, pos, avoid}`, where `avoid` is a list of German sentences, SHALL answer an example for that subject, the part of speech `pos` included, whose German sentence, compared without case, punctuation and extra spacing, is none of the `avoid` sentences and is not the sentence the shared cache holds for the same subject, `pos` included. It SHALL check the session before it reads the body, answer `400` for a body that is not a JSON object, for a blank `word`, and for more than 5 `avoid` sentences or one longer than 300 characters, ignore list entries that are not strings, and map generation failures to the same statuses as `GET /api/examples`. It SHALL NOT join an in-flight generation of the same subject.

#### Scenario: A different example

- **WHEN** an authenticated request names a word and lists its current example in `avoid`
- **THEN** the response is `200` with an example whose sentence is not the listed one
- **AND** the generation request told the model to avoid the listed sentence

#### Scenario: The cached example is avoided too

- **WHEN** the shared cache holds an example for the subject and the request does not list it
- **THEN** the answered sentence is not the cached one

#### Scenario: The model repeats an avoided sentence

- **WHEN** the model answers a sentence that is in `avoid`
- **THEN** that answer is refused and generation is retried
- **AND** it is never returned

#### Scenario: No session

- **WHEN** a request without a valid session posts a regeneration
- **THEN** the response is `401` and the provider is not called

#### Scenario: Blank word

- **WHEN** an authenticated regeneration request has a blank `word`
- **THEN** the response is `400`

#### Scenario: Punctuation does not make a sentence another one

- **WHEN** `avoid` lists "Er kommt heute!" and the model answers "Er kommt heute."
- **THEN** that answer is refused and generation is retried

#### Scenario: Too much to avoid

- **WHEN** an authenticated request lists 6 sentences in `avoid`, or one of 301 characters
- **THEN** the response is `400` and the provider is not called

#### Scenario: Body is not JSON

- **WHEN** an authenticated request carries a body that is not a JSON object
- **THEN** the response is `400`
- **AND** the same body without a session is answered `401`

#### Scenario: The part of speech picks the cached example to avoid

- **WHEN** the cache holds an example for a word as an adjective and another as a noun
- **AND** an authenticated request names the word with `pos` for the adjective
- **THEN** the adjective's cached sentence is avoided, and the noun's is not
