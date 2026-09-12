# push-sync Specification

## Purpose
TBD - created by archiving change push-notified-sync. Update Purpose after archive.
## Requirements
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

### Requirement: Payload-less pokes over authenticated sockets
The backend SHALL accept WebSocket subscriptions on `GET /api/sync/updates` only with a valid account cookie, refusing others with 401, and SHALL send pokes that carry no data beyond "something changed". Pokes SHALL reach only sockets of the updated account.

#### Scenario: Unauthenticated upgrade
- **WHEN** a socket connects without a valid cookie
- **THEN** the upgrade is refused with 401

#### Scenario: Other accounts stay silent
- **WHEN** `userdb-7` changes
- **THEN** sockets subscribed to account 9 receive nothing

### Requirement: Poked clients pull through the normal path
The client SHALL hold the poke socket only while the page is visible, SHALL pull once on every (re)connect and on every poke, and SHALL reconnect on a flat delay while visible and online — going offline stops the retries, the `online` event resumes them and recycles a possibly stale socket. A waiting pairing dialog SHALL close only when a pull delivers the receipt echoing that dialog's nonce: the QR carries the nonce out, the newly paired device writes `pairing:<nonce>` into the replicated database, and confirmation removes every receipt.

#### Scenario: Idle device learns of a remote write
- **WHEN** another device of the same account writes a word
- **THEN** the idle visible device pulls without navigation and the word appears locally
- **AND** the current page reflects it — home recomputes lesson availability without touching a half-typed add form

#### Scenario: Pairing confirmed by its own echo
- **WHEN** the QR carried nonce N and a new device adopts the account and writes receipt `pairing:N`
- **THEN** the dialog closes on the pull that delivers the receipt, and receipts are removed

#### Scenario: Unrelated activity while the dialog waits
- **WHEN** another existing device writes ordinary data, or the poke socket reconnects
- **THEN** the dialog stays open

#### Scenario: Page hidden
- **WHEN** the page becomes hidden
- **THEN** the socket closes, and becoming visible again reconnects and pulls

### Requirement: A pull that writes no document leaves the current screen untouched
A sync pass SHALL report how many documents it pulled and pushed. The current screen SHALL be reloaded — and a waiting pairing dialog checked — only when the pass pulled at least one document; a pass that pulled nothing SHALL change nothing on screen.

#### Scenario: Idle poke with nothing new
- **WHEN** the poke socket reconnects or a poke arrives and the pull writes no document
- **THEN** the current screen is not reloaded and does not re-render

#### Scenario: Pull brings new documents
- **WHEN** a pull writes at least one document
- **THEN** the current screen reloads its data and reflects them

### Requirement: Route entry does not pull again within 30 s of the last completed pass
A navigation SHALL run no sync pass when a pass completed less than 30 s earlier and nothing was written locally since. A poke from the push socket SHALL always run a pass.

#### Scenario: Navigation soon after a pass
- **WHEN** the user opens another screen within 30 s of a completed pass and has written nothing locally since
- **THEN** no pass runs

#### Scenario: Poke within the interval
- **WHEN** a poke arrives within 30 s of a completed pass
- **THEN** a pass runs

