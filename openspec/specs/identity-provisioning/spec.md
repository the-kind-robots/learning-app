# identity-provisioning Specification

## Purpose
Define how accounts and their CouchDB userdbs are provisioned and kept consistent, so a recycled account id cannot inherit someone else's data and an operator can mint invites.

## Requirements

### Requirement: A recycled account id never inherits an existing userdb
Provisioning SHALL refuse to create an account whose derived userdb already exists in CouchDB, leaving the user row unwritten and the stale userdb untouched. The refusal SHALL be observable to the operator.

Provisioning SHALL be atomic across the two stores: the user row commits only once its userdb is secured, so a failed CouchDB step leaves no account row behind, and a userdb created by the failed attempt is removed with it (best effort). The id a failed attempt minted is minted again by the next attempt, not leaked. An attempt that opens no account SHALL leave its invite unspent.

#### Scenario: Stale userdb from a previous owner
- **WHEN** provisioning would assign an id whose `userdb-<id>` already exists
- **THEN** the request fails with 503, no user row is created, and the event is logged

#### Scenario: Clean id
- **WHEN** no userdb exists for the assigned id
- **THEN** the account is created and its userdb is secured with the account's role

#### Scenario: CouchDB step fails mid-provision
- **WHEN** creating or securing the userdb fails after the row was inserted
- **THEN** the insert rolls back, the userdb the attempt created is removed, and the next attempt provisions the same id cleanly
- **AND** the invite the attempt presented still opens an account

### Requirement: Orphan userdbs are reported, never auto-deleted
The system SHALL report every CouchDB `userdb-N` whose account row `N` is missing from SQLite — at startup and on operator demand — and SHALL NOT delete or tombstone any userdb on its own. A restored-from-older-backup SQLite makes such userdbs live and legitimate, so disposal stays a human decision. Account rows without a userdb are a different signal and are not part of this report.

#### Scenario: Staged orphan userdb
- **WHEN** CouchDB holds `userdb-7` and no `users` row with id 7 exists
- **THEN** the report names `userdb-7` and nothing else, and no data is touched

#### Scenario: Stores agree
- **WHEN** every `userdb-N` has a matching `users` row
- **THEN** the report is empty

#### Scenario: CouchDB unreachable at startup
- **WHEN** the startup report cannot enumerate CouchDB databases
- **THEN** a warning is logged and boot continues

### Requirement: An operator mints an invite from the packaged app

The packaged application SHALL expose a `mint-invite` command-line entry point that stores a single-use grant in the configured database, prints the resulting invite URL, and exits without starting the server. The URL SHALL point at the configured public origin and carry the token in the URL fragment, keeping it out of access logs and Referer headers.

#### Scenario: Minting from the jar

- **WHEN** the operator runs the jar with the `mint-invite` argument
- **THEN** database adoption and migrations run first, a single-use grant is stored, the invite URL is printed, and no server starts

#### Scenario: Configured public origin

- **WHEN** `LEARNING_APP__PUBLIC_URL` is set
- **THEN** the printed URL uses that origin, with no doubled slash before the fragment

#### Scenario: Unknown command

- **WHEN** the entry point receives an argument that is not a known command
- **THEN** the process names the unknown command on stderr and exits nonzero

### Requirement: A device without an account requests an invite

`POST /api/invite-requests` SHALL accept `{"sha256": …, "words": …, "days": …}`: the lowercase hex sha256 of a secret the device keeps, its word count and its days of practice, both integers from 0 to 1000000. The request SHALL carry nothing else — no free text, no name, no address — and SHALL create no CouchDB database. A new request SHALL be stored as pending and mailed to the operator once; a hash already requested SHALL answer its current status and mail nothing, unless its status is `expired`: then the old request and its grant SHALL be dropped and the hash requested anew. A request stored now SHALL be answered `201 Created`, one that already exists `200`; both SHALL carry `Location: /api/invite-requests/<sha256>` and `{"status": …}`. A refusal SHALL carry `Retry-After`.

A client address SHALL make at most 5 requests in 24 hours; the next one SHALL be refused with 429 and change nothing. An IPv6 address SHALL count as its /64. The limit SHALL remember at most 10000 networks at once; past that a request from a network it does not know SHALL be refused with 429. All callers together SHALL store at most 50 new requests in 24 hours, and the backend SHALL try at most 100 mails a day; past either, a new hash SHALL be refused with 429, and a hash already requested SHALL still answer its status.

#### Scenario: First request
- **WHEN** a device posts a hash nobody requested, with its stats
- **THEN** the answer is `201 Created` with the request's `Location` and `pending`, the request is stored, and one mail with the stats and the Grant and Decline links goes to the operator

#### Scenario: Repeated request
- **WHEN** a device posts a hash it already requested
- **THEN** the answer is the request's current status and no mail is sent

#### Scenario: Request whose invite expired unused
- **WHEN** a device posts a hash whose status is `expired`
- **THEN** the answer is `pending`, the operator gets a mail with new links, and the old links answer 404

#### Scenario: Malformed request
- **WHEN** the hash is not 64 lowercase hex digits, or a stat is missing, negative, above 1000000 or not an integer
- **THEN** the answer is 400 and nothing is stored or mailed

#### Scenario: Sixth request from one address in a day
- **WHEN** an address has made 5 requests in the last 24 hours and makes another
- **THEN** the answer is 429, and nothing is stored or mailed

#### Scenario: One IPv6 network
- **WHEN** addresses of one IPv6 /64 have made 5 requests in the last 24 hours and another of its addresses makes one
- **THEN** the answer is 429

#### Scenario: Fifty-first new request in a day
- **WHEN** 50 requests were stored in the last 24 hours and a device posts a hash nobody requested
- **THEN** the answer is 429 and nothing is stored or mailed, while a device posting a hash already requested gets its status

#### Scenario: Mail cannot be sent
- **WHEN** the mail to the operator fails
- **THEN** the answer is 503 and the request is not stored, so the device's next request mails again

### Requirement: The operator decides a request from the email

Each link in the mail SHALL open a page with one button, Grant or Decline, and SHALL change nothing when opened: mail scanners fetch links. The button's POST SHALL decide. The links SHALL carry a token of their own, unknown to the device, and the server SHALL store only its sha256. Granting SHALL store the request's hash as a single-use grant with the lifetime a minted invite has, so the device's secret is redeemed by `/api/identity/provision` like any invite. A request SHALL be decided once; a later POST on either link SHALL change nothing and the page SHALL say how the request was decided.

#### Scenario: Opening a link
- **WHEN** the operator, or a mail scanner, opens the Grant link
- **THEN** a page shows the request's stats and a Grant button, and the request is still pending

#### Scenario: Granting
- **WHEN** the operator presses Grant
- **THEN** the request is granted, and the device's secret provisions an account through `/api/identity/provision`

#### Scenario: Declining
- **WHEN** the operator presses Decline
- **THEN** the request is declined and no grant exists for its hash

#### Scenario: Deciding twice
- **WHEN** a decided request's Grant or Decline button is pressed again
- **THEN** its decision stays as it was, the hash has at most one grant, and the page names the decision

#### Scenario: A token nobody was mailed
- **WHEN** a link carries a token no request has
- **THEN** the answer is 404

### Requirement: A device learns how its request was decided

`GET /api/invite-requests/<sha256>` with `Authorization: Bearer <secret>` SHALL answer `{"status": "pending" | "granted" | "declined" | "expired", "links": …}` when the secret's sha256 is `<sha256>`, and 404 when it is not, when the header is missing, or when nobody made that request. `links` SHALL name the next step: `provision` for a granted request, `request` for an expired one. The status of a granted request SHALL stay `granted` after its grant is redeemed, and SHALL become `expired` once its grant expired unused. The page of an expired request SHALL say so.

#### Scenario: Pending request
- **WHEN** a device presents the secret of a request nobody decided
- **THEN** the answer is `pending`

#### Scenario: Decided request
- **WHEN** the operator granted or declined the request
- **THEN** the answer is `granted` or `declined`

#### Scenario: Grant expired unused
- **WHEN** the request was granted and its grant expired before the device redeemed it
- **THEN** the answer is `expired`, and the secret no longer provisions an account

#### Scenario: No request
- **WHEN** a device presents a secret whose hash nobody requested
- **THEN** the answer is 404

#### Scenario: Someone else's request
- **WHEN** a caller reads a request with a secret that is not the one it was made with, or with none
- **THEN** the answer is 404
