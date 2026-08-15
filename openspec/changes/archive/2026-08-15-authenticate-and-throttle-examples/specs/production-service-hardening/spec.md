## ADDED Requirements

### Requirement: An endpoint that spends money upstream is rate limited at its own edge zone

Every endpoint whose requests cost money at a third-party provider SHALL be rate limited by the
reverse proxy in a zone of its own, keyed by client address. It SHALL NOT share a zone with bulk
database traffic: the replication zone is sized for `_changes` and `_bulk_get` bursts, so sharing it
would let ordinary replication spend the paid endpoint's budget, and would size the paid endpoint's
budget for replication.

A throttled response SHALL be `429` and SHALL carry `Retry-After`, so a client backs off on the
server's timetable instead of its own.

The limit SHALL be present in the development proxy config as well as production, so the behaviour is
exercised on the stand rather than first met in production.

#### Scenario: A burst from one client

- **WHEN** one client sends example requests faster than the configured rate, beyond the configured
  burst
- **THEN** the excess requests are answered `429` with a `Retry-After` header
- **AND** those requests never reach the application, so nothing is spent on them

#### Scenario: Ordinary use

- **WHEN** a client requests examples within the configured rate and burst
- **THEN** every request is proxied and answered normally

#### Scenario: Replication traffic

- **WHEN** database replication produces a burst against `/db/`
- **THEN** it is accounted against the replication zone only, and leaves the example endpoint's
  allowance untouched
