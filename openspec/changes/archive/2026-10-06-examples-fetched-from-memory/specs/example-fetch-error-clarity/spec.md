## ADDED Requirements

### Requirement: Every answer to an example request falls into one kind
The client SHALL classify every answer to an example request as exactly one of these, and SHALL NOT let a request end in an unclassified error. A subject that cannot be put in a URL, such as one holding half of a character written as two UTF-16 units, SHALL be classified before anything is sent:

| Kind | Answer |
|---|---|
| `:failure/invalid-subject` | the subject cannot be put in a URL, so nothing is sent |
| an example, no failure | a success whose body is an example with its sentence and its translation |
| `:failure/invalid-response` | a success whose body is not JSON, or lacks the sentence or the translation |
| `:failure/rejected` | any 4xx other than 401, 403, 408, 425 and 429 — 400, 404 and 422 among them |
| `:failure/unauthorized` | 401 or 403 |
| `:failure/throttled` | 429 |
| `:failure/unavailable` | any 5xx, and 408 or 425 — the server timed out or refused early data, which says nothing about the subject |
| `:failure/network` | no answer at all: the request failed before a status arrived, or a success body broke off while it arrived |
| `:failure/aborted` | the client aborted the request |

A `Retry-After` header that is a whole number of seconds SHALL be carried with the kind; any other header, such as an HTTP date or `30 seconds`, SHALL be ignored. What each kind does is stated in `specs/example-backfill/spec.md`.

#### Scenario: A provider outage
- **WHEN** `/api/examples` answers 503 with `Retry-After: 30`
- **THEN** the answer is `:failure/unavailable`, with a 30 s retry hint

#### Scenario: A timeout the server answered
- **WHEN** `/api/examples` answers 408
- **THEN** the answer is `:failure/unavailable`

#### Scenario: A subject the generator cannot answer
- **WHEN** `/api/examples` answers 422
- **THEN** the answer is `:failure/rejected`

#### Scenario: Half an emoji in a theme name
- **WHEN** the subject's collection name ends in half of a character written as two UTF-16 units
- **THEN** the answer is `:failure/invalid-subject` and no request is sent

#### Scenario: The network is down
- **WHEN** the request fails before any status arrives
- **THEN** the answer is `:failure/network`

## MODIFIED Requirements

### Requirement: Example fetch rejects malformed success payloads before local save
The system SHALL reject malformed example payloads before attempting to save them as local example documents.

#### Scenario: Backend success body misses required fields
- **WHEN** `/api/examples` returns a success response that lacks required example fields
- **THEN** the client classifies it as `:failure/invalid-response` and saves nothing
- **AND** that pair is not asked for again in the page's life

### Requirement: Example generation unavailability is surfaced explicitly
The system SHALL return an explicit non-success response when backend example generation is unavailable.

#### Scenario: Development backend has no API key
- **WHEN** example generation is unavailable because required backend configuration is missing
- **THEN** `/api/examples` returns a non-200 response with a clear error message
- **AND** the warning the client logs names the failure kind, the status and the server's message rather than `missing required fields`
