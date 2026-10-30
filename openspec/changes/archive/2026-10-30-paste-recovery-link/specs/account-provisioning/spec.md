## ADDED Requirements

### Requirement: A device without an account pastes a recovery link
A device without an account SHALL accept a recovery link pasted into its sync menu and SHALL adopt its key exactly as if the link had been opened: the link's fragment, `pair=` included, is read on the next boot (Requirement: Adopt account on key entry with data merge). A text that is not a URL whose fragment carries `key=` SHALL be refused with a message, and SHALL change neither the address nor any data.

#### Scenario: The installed app joins an account
- **WHEN** a device without an account pastes the recovery link of an account into its sync menu
- **THEN** the device adopts that account and offers the account actions

#### Scenario: A pairing link
- **WHEN** the pasted link carries `pair=<nonce>`
- **THEN** the device writes the pairing receipt of that nonce, as when the link is opened

#### Scenario: Not a recovery link
- **WHEN** the pasted text is not a link with a `key=` fragment
- **THEN** the message «Это не ссылка восстановления» is shown and the device still has no account

## MODIFIED Requirements

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
- **THEN** the key is the token itself, carried by the pairing QR or a client-composed link, which may be opened or pasted whole — the token is never typed
- **AND** the server never receives a user email address
