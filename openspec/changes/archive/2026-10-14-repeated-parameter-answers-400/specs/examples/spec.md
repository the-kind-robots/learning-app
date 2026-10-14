## ADDED Requirements

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
