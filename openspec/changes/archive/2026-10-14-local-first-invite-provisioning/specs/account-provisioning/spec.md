## ADDED Requirements

### Requirement: Local-first default
The system SHALL operate fully on-device by default and SHALL NOT create any server-side account or database on first launch or for users who never opt into cross-device sync.

#### Scenario: Fresh launch creates no server resource
- **WHEN** the app is launched on a device with no stored account token
- **THEN** all data is read from and written to the local PouchDB only
- **AND** no server account or `userdb` is created and no network identity call is made

#### Scenario: Public visitor cannot provision
- **WHEN** a request reaches the server without a valid grant
- **THEN** no `userdb` is created

### Requirement: Account creation requires a valid invite grant
The system SHALL create a server account and its `userdb` only when presented with a valid, unexpired, single-use invite token, and SHALL burn the token on successful provision.

#### Scenario: Provision with valid invite
- **WHEN** a device submits a provision request with a valid unused invite
- **THEN** the server mints a bearer token, creates the account row holding its hash and the role-secured `userdb`, and returns `{id, token}`
- **AND** the invite is marked used

#### Scenario: Provision with used or expired invite
- **WHEN** a device submits a provision request with an already-used or expired invite
- **THEN** the server rejects the request and creates no `userdb`

#### Scenario: Invite arrives as a link
- **WHEN** the app is opened via an `#invite=<token>` link
- **THEN** the device provisions against that invite on boot

#### Scenario: Provisioned device ignores an invite
- **WHEN** an `#invite=` link is opened on a device that already has an account
- **THEN** no provision request is made and the invite is not burned

#### Scenario: Operator mints invites
- **WHEN** the operator mints an invite via the REPL
- **THEN** a single-use, expiring token is generated, returned in plaintext once, and stored hashed (sha256)

### Requirement: Bearer-token identity
The account identity SHALL be a single high-entropy bearer token. The server SHALL store only its sha256 against the account and SHALL authenticate every request by looking that hash up — there is no password, no session, and no separate login step.

#### Scenario: Token authenticates per request
- **WHEN** a request to the user's `userdb` carries the account token as the cookie
- **THEN** the auth check resolves the account from the token's sha256 and authorizes the matching CouchDB role

#### Scenario: Unknown token is rejected
- **WHEN** a request carries no token or a token matching no account
- **THEN** the auth check returns unauthorized

#### Scenario: Key is the token, transported client-side
- **WHEN** a user shares their account
- **THEN** the key is the token itself, carried by the pairing QR or a client-composed link — there is no manual entry
- **AND** the server never receives a user email address

### Requirement: A credential never travels in a request URL
Keys and invites SHALL be carried in the URL fragment, which browsers do not send to the server, so a permanent credential cannot reach access logs or `Referer` headers. The application SHALL clear the fragment once it has been read.

#### Scenario: Opening a key link leaves no trace on the server
- **WHEN** a device opens a link carrying a key or an invite
- **THEN** the server's request log contains no part of the credential

#### Scenario: The address bar is cleared
- **WHEN** the credential has been read on boot
- **THEN** the fragment is gone from the address bar and from the history entry, so navigating back does not restore it

### Requirement: Recovery artifact is the token itself
The system SHALL use the durable account token as the recovery artifact — no expiry, no burn-on-use. One-time semantics SHALL apply only to invites. Revocation SHALL be by rotation only (a new token replaces the old); there is no per-device logout.

#### Scenario: Recovery link composed locally
- **WHEN** the user asks for a recovery link
- **THEN** the client composes a link carrying the token from the stored identity, with no server call

#### Scenario: Saved recovery link stays valid
- **WHEN** a saved recovery link is used a second time, or after a long delay
- **THEN** it still adopts the account (until the token is rotated)

### Requirement: Adopt account on key entry with data merge
When a key is entered, the system SHALL validate the token against the server, then adopt the account by merging local and account data, except when the device currently belongs to a different account, in which case it SHALL replace the local database with the account's data. A destructive wipe SHALL happen only after the token validates and a confirmed account-id mismatch.

#### Scenario: Invalid key adopts nothing
- **WHEN** a key is entered whose token authenticates no account
- **THEN** no identity is saved and the local database is not touched

#### Scenario: Merge into same or empty account
- **WHEN** a valid key is entered on a device that has no account or the same account
- **THEN** local data is kept and merged into the account via replication union

#### Scenario: Replace on cross-account switch
- **WHEN** a valid key is entered on a device that currently belongs to a different account
- **THEN** the local database is wiped and replaced with the entered account's data

### Requirement: Account id is derived from the token
The system SHALL let a device that holds only the token learn its account id from the server, so the key need not carry the id.

#### Scenario: Adopting device resolves its account
- **WHEN** a device presents the token and asks for its account
- **THEN** the server returns the account id the token authenticates, or unauthorized for an unknown token

### Requirement: Eager claim provisioning is removed
The system SHALL NOT expose an unauthenticated endpoint that creates a server account; the prior `POST /api/identity/claim`, the password login, and the one-time recovery-token endpoints SHALL be removed.

#### Scenario: Claim endpoint gone
- **WHEN** a client calls the former `/api/identity/claim`
- **THEN** the route does not exist and no account is created

### Requirement: Auth cookie is a derived cache protected by durable storage
The account token's durable home SHALL be local device storage (IndexedDB); the auth cookie SHALL be derived from it and re-established on each launch, so cookie eviction does not lose sign-in. The system SHALL request persistent storage to shield that durable store from automatic eviction.

#### Scenario: Cookie rebuilt on launch
- **WHEN** the app launches on a device that has a stored token but no auth cookie
- **THEN** the cookie is re-written from the stored token with no network call

#### Scenario: Persistent storage requested
- **WHEN** the app boots
- **THEN** it requests persistent storage for its origin
