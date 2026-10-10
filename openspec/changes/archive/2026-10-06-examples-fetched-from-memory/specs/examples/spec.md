## ADDED Requirements

### Requirement: Every example request carries the session, and none goes before it is written

The client SHALL send the session cookie with each example request. The request SHALL declare its
credentials mode explicitly rather than depend on the browser default for same-origin URLs, because
the default stops applying the moment the request URL becomes cross-origin, and the resulting failure
is silent — examples stop arriving and nothing reports why.

The client SHALL NOT send an example request before the session cookie of this start has been
written. A request sent earlier would race the component that writes the cookie, and the outcome of
that race decides whether the first example of the boot is answered or refused.

#### Scenario: Fetching an example

- **WHEN** the client requests an example for a word
- **THEN** the request carries the session cookie
- **AND** the endpoint authenticates it as the account that owns the session

#### Scenario: Missing examples at boot

- **WHEN** the app starts holding entries without examples
- **THEN** no example request is sent before the session cookie has been written
- **AND** the requests it then sends authenticate like any other

## REMOVED Requirements

### Requirement: The client sends its session with every example request

**Reason**: Its boot scenario was about a stored task; there are none. Restated about requests.
**Migration**: Requirement: Every example request carries the session, and none goes before it is
written.

### Requirement: Example fetch tasks are created on vocabulary entry creation

**Reason**: Creating an entry writes no task. The new entry is in memory, and memory is what the
device asks for examples from.
**Migration**: Requirement: The device asks for the examples memory is missing
(`specs/example-backfill/spec.md`), scenario "A word added on this device".
