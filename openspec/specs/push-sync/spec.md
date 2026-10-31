# push-sync Specification

## Purpose
Define how a change on one device reaches the others promptly: the backend watches CouchDB and pokes the account's open clients, which then pull through the normal sync path without redundant passes or needless redraws.

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
A sync pass SHALL report how many documents it pulled and pushed. The documents a pass pulls SHALL reach the learner's data in memory and SHALL show on the next screen opened; no pass SHALL change the screen on display. A waiting pairing dialog SHALL be checked only when the pass pulled at least one document.

#### Scenario: Idle poke with nothing new
- **WHEN** the poke socket reconnects or a poke arrives and the pull writes no document
- **THEN** the current screen does not change

#### Scenario: Pull brings new documents
- **WHEN** a pull writes at least one document
- **THEN** the current screen does not change
- **AND** the next screen opened shows them

### Requirement: The device-sync control is on the home page only
The system SHALL offer the device-sync control, named «Синхронизация», in the header of the home page only, with or without an account. The control SHALL draw a laptop beside a phone and SHALL stand right after the word mark, centred on its line. Every other page SHALL render no such control.

The control SHALL show the state of the sync: grey on a device without an account; green with a glow, the colour of the lesson's light bulb, on a device with an account while it is online; the same green faded, without the glow, while it is offline.

#### Scenario: Home page
- **WHEN** a device, with an account or without, opens the home page
- **THEN** the header shows the «Синхронизация» control right after the word mark

#### Scenario: Another page with an account
- **WHEN** a device with an account opens the words list, a lesson or the themes screen
- **THEN** the header shows no «Синхронизация» control

#### Scenario: No account
- **WHEN** a device without an account opens the home page
- **THEN** the «Синхронизация» control is grey

#### Scenario: Online
- **WHEN** a device with an account is online
- **THEN** the «Синхронизация» control is green with a glow

#### Scenario: Offline
- **WHEN** a device with an account goes offline
- **THEN** the «Синхронизация» control is faded green without a glow
- **AND** it is green with a glow again once the device is back online

### Requirement: A device without an account asks for an invite
The sync menu of a device without an account SHALL offer «Сохранить мои слова». Activating it SHALL close the menu and open a form, «Чтобы слова не потерялись», that asks, warmly, for a few words and says the icon beside the word mark turns green once it is ready, with one field, «ОТПРАВИТЬ» and «Отмена». The field SHALL start empty, and an empty field SHALL NOT send. Sending SHALL close the form. Until the invite service exists (#650) the request goes no further than the device.

#### Scenario: Menu without an account
- **WHEN** a device without an account opens the sync menu
- **THEN** the menu offers «Сохранить мои слова»

#### Scenario: Request sent
- **WHEN** the learner writes a few words in the invite form and sends them
- **THEN** the form closes

#### Scenario: Nothing written
- **WHEN** the learner sends the invite form with the field empty
- **THEN** the form stays open

### Requirement: Sync passes run one at a time
The client SHALL run at most one sync pass at a time. A request for a pass while none runs SHALL start one. Requests that arrive while a pass runs SHALL cause exactly one more pass after it, however many arrive. Every requester SHALL be answered when the passes finish, not before. A failed pass SHALL NOT stop the next request from running one. Route entry, a poke, the `online` event and a local write SHALL all request a pass this way.

#### Scenario: Triggers at once
- **WHEN** route entry, a poke, the `online` event and a local write all ask for a pass while none runs
- **THEN** one pass runs

#### Scenario: Request during a pass
- **WHEN** one or more requests arrive while a pass runs
- **THEN** exactly one more pass runs after it

#### Scenario: Route entry soon after a pass
- **WHEN** the user opens another screen after a pass has completed
- **THEN** a pass runs

### Requirement: Local writes are batched and pulled documents request nothing
Local writes SHALL request a pass at most once every 3 s, so a burst of writes goes out in few passes. A document revision the pass itself pulled SHALL NOT count as a local write and SHALL NOT request a pass.

#### Scenario: A pull writes documents
- **WHEN** a pass pulls documents from the server and nothing else is written locally
- **THEN** no further pass follows it

#### Scenario: A lesson's reviews
- **WHEN** a learner answers several trials within 3 s and no pass is running
- **THEN** their writes go out in at most two passes

### Requirement: A pull that wrote collections resolves their conflicts
After a pass that pulled documents, the client SHALL resolve the conflicts of every collection among them as the collections requirement states, in the same pass as it resolves those of words. A pass that pulled nothing SHALL resolve nothing.

#### Scenario: A pull brings a sibling version
- **WHEN** a pass pulls a collection that this device changed too
- **THEN** the collection has one live version when the pass ends
