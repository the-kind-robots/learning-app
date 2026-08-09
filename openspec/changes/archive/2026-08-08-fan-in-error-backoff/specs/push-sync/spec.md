# push-sync Delta

## MODIFIED Requirements

### Requirement: One fan-in feed per node

The backend SHALL watch CouchDB's `_db_updates` feed in a single longpoll loop per node and SHALL map each updated `userdb-N` to account `N`. The loop SHALL start and stop with the HTTP server, survive failed feed turns by backing off before the next attempt, and never crash the server. The backoff SHALL depend on the failure: an authentication refusal (401/403) waits minutes and logs an error; any other failure waits seconds and logs a warning.

#### Scenario: Account database updated

- **WHEN** any write lands in `userdb-7`
- **THEN** account 7 is poked once for that feed turn, regardless of how many of its documents changed

#### Scenario: CouchDB briefly down

- **WHEN** a feed turn fails for any reason other than an authentication refusal
- **THEN** the loop logs a warning, waits a few seconds, and resumes from `now` — reconnecting clients pull anyway

#### Scenario: CouchDB refuses the credentials

- **WHEN** a feed turn is answered with 401 or 403
- **THEN** the loop logs an error and waits minutes before the next attempt, so steady retries cannot trip the server's authentication lockout
