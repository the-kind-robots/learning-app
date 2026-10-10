# example-fetch-error-clarity Specification

## Purpose
Define how example fetching handles a malformed response or an unavailable generator, so neither becomes a broken example saved on the device.

## Requirements

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

### Requirement: A failed generation answers with a status that says whose problem it is

When `/api/examples` cannot serve an example to an authenticated request, its status SHALL tell the
client whether the failure belongs to the pair or to the provider.

The pair is the problem, and the response SHALL be `422`, when:

- the provider answered with content and no candidate passed the checks — content that is not JSON
  (a reply cut off at the token limit, or prose), JSON that is not an example object, and an example
  whose `structure` items are not objects all count as candidates the checks reject;
- the provider's moderation refused the input (a `403` whose error names moderation reasons), or
  the model refused it (its output was filtered, or it answered with a refusal).

The service is the problem, and the response SHALL be `503` with `Retry-After: 30`, when the
dictionary could not be read, or when the provider gave no completion — an error object in a `200`
answer, no choices, or empty content — could not be reached, did not answer within the per-attempt
timeout, or answered with any other error status, `401`, `402` and a `403` without moderation
reasons included.

When the provider throttled the request with `429`, the response SHALL be `429`, with the
provider's `Retry-After` when it sent one that can be read. A `Retry-After` is read as
delta-seconds or as an HTTP-date in any of the forms RFC 9110 defines, a date in the past is no
delay, and a delay is held to at most one hour. A value that cannot be read SHALL be treated as no
header at all, never as a failure of the request. No delay is no header: a `429` whose delay
is nothing SHALL carry no `Retry-After` of the application's, never a rounded-up second.

A generation tries a pair up to three times. A refusal of the input, any `4xx` other than `408`, and
an empty answer cut off at the token limit SHALL end the generation without another attempt: a
retry would be refused the same way. Otherwise the last attempt SHALL decide the status: a provider
failure after rejected candidates is a provider failure, and a rejected candidate after a provider
failure is a pair failure.

The endpoint SHALL answer `401` only for its own session check. The provider refusing the
service's credentials SHALL NOT reach the client as `401` or `403`: that is the service's fault,
not the session's.

#### Scenario: No candidate passes the checks

- **WHEN** every attempt for a pair returns content the checks reject
- **THEN** the response is `422`

#### Scenario: Every reply is cut off

- **WHEN** every attempt returns JSON cut off at the token limit
- **THEN** the response is `422`

#### Scenario: Moderation refuses the input

- **WHEN** the provider answers `403` with moderation reasons
- **THEN** the response is `422`
- **AND** the provider is not asked again

#### Scenario: The provider throttles

- **WHEN** the provider answers `429` with `Retry-After: 7`
- **THEN** the response is `429` with `Retry-After: 7`

#### Scenario: The provider names an unreadable delay

- **WHEN** the provider answers `429` with a `Retry-After` that is neither delta-seconds nor an
  HTTP-date
- **THEN** the response is `429` without a `Retry-After` of the application's

#### Scenario: The provider names a date already past

- **WHEN** the provider answers `429` with a `Retry-After` date that has already passed
- **THEN** the response is `429` without a `Retry-After` of the application's

#### Scenario: The provider gives no completion

- **WHEN** the provider answers `200` with an error object, no choices or empty content on the last
  attempt
- **THEN** the response is `503` with `Retry-After: 30`

#### Scenario: The provider is unreachable

- **WHEN** the provider cannot be reached, times out or answers with a `5xx` on the last attempt
- **THEN** the response is `503` with `Retry-After: 30`

#### Scenario: The dictionary cannot be read

- **WHEN** the dictionary fails or does not answer
- **THEN** the response is `503` with `Retry-After: 30`

#### Scenario: The provider is out of credits

- **WHEN** the provider answers `402`
- **THEN** the response is `503` with `Retry-After: 30`
- **AND** the provider is not asked again

#### Scenario: The provider refuses the service's key

- **WHEN** the provider answers `401`, or `403` without moderation reasons
- **THEN** the response is `503` with `Retry-After: 30`, not `401` or `403`

### Requirement: The proxy waits longer than the longest generation

A generation SHALL make at most three attempts, each waiting at most the per-attempt timeout: 30
seconds unless `EXAMPLE_GENERATION_TIMEOUT_MS` sets another positive number of milliseconds. A value
that is not one SHALL be logged and the default used. The timeout SHALL be clamped so that three
attempts and a margin fit under the proxy's wait; a value the clamp lowered SHALL be logged once.

The reverse proxy SHALL wait 100 seconds for `/api/examples`, in production and in development:
longer than three attempts at any accepted timeout, by construction. So a request whose example was
paid for on the last attempt is answered by the application, not by the proxy's `504`.

A slow dictionary is outside the clamp: up to three sequential reads at the HTTP client's own
timeout can still push a request past the proxy's wait. That rare case is accepted, and the
proxy's `504` is its bound.

#### Scenario: Every attempt runs to its timeout

- **WHEN** the provider never answers
- **THEN** each of the three attempts waits its timeout
- **AND** the response is the application's `503`, not the proxy's `504`

#### Scenario: A configured timeout the proxy could not cover

- **WHEN** `EXAMPLE_GENERATION_TIMEOUT_MS` is set to `60000`
- **THEN** each attempt waits at most the clamped timeout, and three attempts fit under the
  proxy's wait
- **AND** the clamp is logged once

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
