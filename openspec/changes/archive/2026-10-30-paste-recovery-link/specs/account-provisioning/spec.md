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

### Requirement: A device without an identity adopts the token of its auth cookie
On boot, a device that holds no identity and opens no credential link SHALL adopt the token of its auth cookie as if that token had arrived as a key. iOS copies Safari's cookies, and not its storage, into an app added to the home screen, so the installed app keeps the account Safari had.

#### Scenario: The app installed from a signed-in Safari
- **WHEN** a device with no identity boots holding the auth cookie of an account
- **THEN** the device adopts that account and offers the account actions

### Requirement: Telegram's browser leaves a credential link for another browser
Telegram's in-app browser keeps its own storage, so an account adopted or an invite redeemed there is lost to Safari and to the installed app. A page that boots with a `#key=` or `#invite=` fragment in Telegram's browser, known by its `TelegramWebviewProxy` object, SHALL adopt nothing and redeem nothing. It SHALL show the dialog «Откройте ссылку в Safari», whose «Скопировать ссылку» copies the whole link, fragment included.

#### Scenario: A recovery link opened from Telegram
- **WHEN** a recovery link is opened in Telegram's browser
- **THEN** the dialog «Откройте ссылку в Safari» is shown and the device adopts no account
- **AND** «Скопировать ссылку» copies the link with its `key=` and `pair=`

#### Scenario: An invite opened from Telegram
- **WHEN** an invite link is opened in Telegram's browser
- **THEN** the dialog «Откройте ссылку в Safari» is shown and no provision request is made

## MODIFIED Requirements

### Requirement: Auth cookie is a derived cache protected by durable storage
The account token's durable home SHALL be local device storage (IndexedDB); the auth cookie SHALL be derived from it and re-established on each launch, so cookie eviction does not lose sign-in. The cookie SHALL be kept for 400 days rather than for the browser session. The system SHALL request persistent storage to shield that durable store from automatic eviction.

#### Scenario: Cookie rebuilt on launch
- **WHEN** the app launches on a device that has a stored token but no auth cookie
- **THEN** the cookie is re-written from the stored token with no network call

#### Scenario: Persistent storage requested
- **WHEN** the app boots
- **THEN** it requests persistent storage for its origin

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
