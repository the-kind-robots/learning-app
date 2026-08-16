## ADDED Requirements

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

### Requirement: The client sends its session with every example request

The client SHALL send the session cookie with each example request. The request SHALL declare its
credentials mode explicitly rather than depend on the browser default for same-origin URLs, because
the default stops applying the moment the request URL becomes cross-origin, and the resulting failure
is silent — examples stop arriving and nothing reports why.

The client SHALL NOT run example fetch tasks before the session cookie has been written. A queued
task from an earlier session would otherwise race the component that writes the cookie, and the
outcome of that race decides whether the first example of the boot is answered or refused.

#### Scenario: Fetching an example

- **WHEN** the client requests an example for a word
- **THEN** the request carries the session cookie
- **AND** the endpoint authenticates it as the account that owns the session

#### Scenario: A queued fetch task at boot

- **WHEN** the app starts with an example fetch task already in the queue
- **THEN** the task runner does not start until the session cookie has been written
- **AND** the request it issues authenticates like any other
