## Why

The shipped sync feature provisions eagerly: the first app load calls `POST /api/identity/claim` and the server immediately creates a permanent CouchDB `userdb`, which is never deleted. Any anonymous visitor — or an attacker dripping requests — can pile unbounded empty databases until the node degrades. Per ADR-0006, the fix is local-first by default (no server resource until the user opts in) plus a grant gate on account creation. Phase 1 uses invites, enough to dogfood and onboard testers without payment infrastructure.

## What Changes

- **BREAKING**: Remove eager `claim`-time database creation. First launch provisions nothing server-side; all data lives only in the local PouchDB `user-db`. `POST /api/identity/claim` is removed.
- Provision a server account (and its `userdb`) only against a valid single-use invite token. Grants get their own table named for the gate slot (`grants` — a phase-2 payment mints rows in the same table; a grant pre-dates any account) reusing the sha256 burn-on-use mechanics; minting is a REPL function — the operator is the developer.
- **Identity becomes a single high-entropy bearer token**, replacing the `{id, secret}` + argon2 + session apparatus. The server stores only `sha256(token) → account` inline on the `users` row; there is no password, no argon2, no `sessions` table, no `/api/identity/auth`, no Ring session middleware. A device proves itself by presenting the token as a cookie; `/auth/check` (already an nginx subrequest per `/db/` call) does the sha256 lookup and emits the CouchDB role. The token is the credential, the cookie, and the session collapsed into one.
- The user's key **is the token itself** — one opaque string, URL- and QR-safe as is (no packing, no `id.secret`). The pairing QR and shareable links carry it in the URL **fragment** (`#key=`, `#invite=`), which the browser never sends to the server — a query string would put a permanent credential in access logs and `Referer` headers. No manual entry.
- The saved recovery artifact is the key (token) — durable, no expiry, no burn. The one-time recovery-token endpoints and table are removed (their burn-on-use role belongs to invites). No per-device logout: rotation (mint new token, drop the old) is the only revocation.
- Entering a key on a device **adopts** that account: the device asks the server which account the token authenticates (`POST /api/identity/account`, token in the body) and adopts it only if an account comes back, so an invalid key leaves the device untouched; it then merges when it has no account or the same account (replication union, local pushed up), or wipes-and-replaces only on a confirmed cross-account switch. (Replaces the current unconditional local-db destroy on pairing.)
- A newly provisioned account is seeded by the device pushing its local `user-db` up over normal replication — no payload upload at provision.
- The auth cookie is set **client-side** from `device-db` (the durable home of the token) and rebuilt on every boot — in an offline-first PWA the cookie is the fragile store (ITP/eviction) and IndexedDB the durable one, so truth lives in `device-db` and the cookie is a derived cache. `navigator.storage.persist()` is requested at boot to shield `device-db` from eviction.
- Export becomes a dump of **every** `user-db` doc (the hand-kept type list already dropped collections silently); import dispatches by `:type` with an insert-if-absent default. Schema version bumps. The manual export/import UI (words-page `⋮` menu) is removed — the functions stay for cold restore and REPL use.
- Reaper and `last_synced_at` are deferred for the invite phase — orphans accumulate at most one per cross-account adoption.
- Payment as a second grant source is explicitly deferred (ADR-0006 phase 2); the gate is designed pluggable so it drops in without rework.

## Capabilities

### New Capabilities
- `account-provisioning`: local-first default; server account + `userdb` created only via a valid grant (phase-1 invite, single-use); single bearer-token identity validated per request at `/auth/check`; adopt-on-key-entry with merge vs cross-account wipe; rotation as the only revocation. Gate is pluggable for a later payment source.
- `account-backup`: export as a full `user-db` dump (versioned), import by `:type` dispatch; account seeding via first-sync push; export/share is client-side only (no server-held PII).

### Modified Capabilities
<!-- None. The shipped sync/identity behavior was never captured in a spec, so it is introduced fresh under account-provisioning rather than modifying an existing capability. -->

## Impact

- **Client**: `src/client/sync.cljs` (no `ensure-identity!`/`claim!` at startup; replication only with a stored token; `start!` sets the cookie via `use-identity!`; `check-incoming-auth!` reads `#key=` and `#invite=` from the fragment), `src/client/adapters/identity.cljs` (`claim!`/`auth!` gone; `provision!`, `use-identity!` cookie writer, `account-id!`), `src/client/adapters/data_export.cljs` (full-dump export, type-dispatched import), `src/client/application.cljs` (`account-key-url` carries the token; export/import UI removed), `src/client/main.cljs` (`:storage/persistent` requests durable storage).
- **Backend**: `src/backend/core.clj` (drop `claim`, `recovery-token`, `redeem`, `auth` routes and the session middleware/`Sessions` store; add invite mint REPL fn + `POST /api/identity/provision` + `POST /api/identity/account`; `/auth/check` reads the cookie token; `random-token` for grants and accounts; cookie parsing via `ring.middleware.cookies`).
- **Schema**: `initial-setup.sql` — single `users (id, token_sha256, created_at)`; `grants` table (sha256 PK, expires_at, used_at nullable); `sessions` and `recovery_tokens` dropped.
- **Migration**: existing eagerly-created `userdb`s and user rows are dev/test artifacts — drop them, recreate the schema, mint one operator invite to self-provision.
- Reworks behavior on branch `feature/sync-delivery`. References ADR-0006.
