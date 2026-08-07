## ADDED Requirements

### Requirement: One fan-in feed per node
The backend SHALL watch CouchDB's `_db_updates` feed in a single longpoll loop per node and SHALL map each updated `userdb-N` to account `N`. The loop SHALL start and stop with the HTTP server, survive CouchDB outages by retrying after a delay, and never crash the server.

#### Scenario: Account database updated
- **WHEN** any write lands in `userdb-7`
- **THEN** account 7 is poked once for that feed turn, regardless of how many of its documents changed

#### Scenario: CouchDB briefly down
- **WHEN** a feed turn fails
- **THEN** the loop logs a warning, waits, and resumes from `now` — reconnecting clients pull anyway

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

#### Scenario: Pairing confirmed by its own echo
- **WHEN** the QR carried nonce N and a new device adopts the account and writes receipt `pairing:N`
- **THEN** the dialog closes on the pull that delivers the receipt, and receipts are removed

#### Scenario: Unrelated activity while the dialog waits
- **WHEN** another existing device writes ordinary data, or the poke socket reconnects
- **THEN** the dialog stays open

#### Scenario: Page hidden
- **WHEN** the page becomes hidden
- **THEN** the socket closes, and becoming visible again reconnects and pulls
