# 0006. Local-first abuse-resistant identity provisioning

- Status: accepted
- Date: 2026-06-04 (revised 2026-08-03)

## Context

Product anonymous by design: no account, email, phone. Current sync provisions eager — first load calls `POST /api/identity/claim`, server immediately makes SQLite user row + CouchDB `userdb-<id>`. Endpoint unauthenticated, ignores request. App is public (sprecha.de) → any random visitor provisions.

Server never deletes databases → database count monotonic. Unauthenticated + free + permanent + monotonic = open abuse vector. Anyone drips `claim` calls — slow, distributed — piles unbounded empty CouchDB databases. Per-IP rate limit bounds inflow, not immortal stock → no prevent node death. CouchDB rots at high database count (shard files, descriptors, `_dbs`, startup scans) even when databases empty.

Sybil defense needs a gate on provisioning with a controlled grant. Permanent identity anchors (account/email/phone) contradict anonymity. A **bearer grant token** gates provisioning without identity: operator controls who gets one, app layer stays PII-free. Phase 1 = **invites** — enough to dogfood + onboard testers now, no payment infra. Payment is the planned phase-2 grant source once demand is proven; the gate is the same slot.

## Decision

Default local-first. First launch provisions nothing server-side; all data (vocab, reviews, collections, examples) lives only in local PouchDB `user-db`. Replication needs a server identity to start. Eager `claim`-time database creation removed (public app → random visitor must not provision).

A server database is born only via a valid grant, on opt-in to multi-device or recovery. Phase-1 grant = single-use invite token:

1. Operator mints invite tokens — random, unguessable, single-use, revocable. Own `grants` table (a grant pre-dates any account, so no user reference; named for the gate slot — a phase-2 payment mints rows in the same table) reusing the sha256 burn-on-use mechanics already proven by recovery tokens.
2. Trigger (provision a new identity) → device submits the invite.
3. Server verifies invite unused + unexpired, burns it, then makes `userdb-<id>`, secures by role, mints a credential token. The device seeds the fresh `userdb` by pushing its local data over normal replication — no second ingest path.

Provision only on valid grant → no anonymous free creation. Invite = bearer capability, not identity → app layer stores no PII.

Identity = one high-entropy **bearer token**, not a username/password pair. The server stores `sha256(token)` inline on the account row (`users (id, token_sha256)` — same hashed-bearer shape as `grants`, one token per account); there is no `password`, no argon2, no session table, no login endpoint. A device proves itself by presenting the token as a cookie on every request; `/auth/check` (already an nginx subrequest per `/db/` call) does the sha256 lookup and emits the CouchDB role `u:<id>`. The token is the credential, the cookie, and the "session" collapsed into one thing — exactly "the device sends its key on every request".

Why a token and not the prior `{id, secret}` + argon2 + session: argon2 exists to slow brute force on *low-entropy human passwords*; our secret is 256-bit random, where sha256 is already infeasible to brute-force. The session table existed only to avoid running argon2 per request — drop argon2 and the session disappears with it. The whole username/password/login/session apparatus was password-login machinery cargo-culted onto an anonymous high-entropy key.

The key the user carries is the token itself — one opaque string, nothing packed around it. A device holding one asks the server which account it belongs to, and adopts it only if the answer comes back, so an invalid key changes nothing on the device.

A credential rides in the URL **fragment** (`#key=`, `#invite=`), never in the query string. A fragment is not part of the request: a query lands in the server's access log and in `Referer` headers, which is no place for a permanent credential. The router drops the fragment when it redirects the unmatched `/`, replacing the history entry rather than pushing one, so the token leaves the address bar and the back button with it.

Transports — QR (pairing) and a shareable link (incl. client-composed email — never server-sent, no PII); no manual entry, the link or QR is the only door. The invite travels the same way, redeemed on open.

The saved recovery artifact **is the key itself** — durable, no expiry, no burn. A one-time wrapped link burns on first use or expires while the user believes they hold a backup; that trap is removed together with the old recovery-token flow. One-time burn-on-use semantics remain only where they belong: invites.

**No per-device logout.** Every device that holds the key can re-present it, so deleting one device's cookie server-side is theater — the device just sends the key again. Meaningful per-device revocation would need leaf tokens a device cannot re-mint, plus a master key kept off devices and one-time pairing grants — real machinery for negligible value in a personal vocab app. So the only revocation is **rotation**: mint a new token, overwrite `users.token_sha256`, re-distribute the new key to your devices (re-scan QR / re-open the link). All-or-nothing, which is honest. One token per account inline on the row — a one-to-many `credentials` table (for rotation overlap or future non-re-mintable leaf tokens) was considered and dropped as premature, since it duplicated the account id across two tables for capabilities we do not need yet.

Entering a key on a device adopts that account:
- device has no account, or the same account → **merge** (replication union; local data pushed up, not wiped);
- device was on a different account → wipe local, adopt the new account's data.

The gate (invite, later payment) sits on **creating a new account** (new `userdb`); adopting an existing key on more devices is free and ungated — the account is already granted (Mullvad: account creation gated, devices free).

The gate is pluggable: payment (Stripe Checkout, settled-payment grant) drops into the same point in phase 2 — no rework of local-first, backup, sync, or reaper.

The export payload (manual backup / cold restore) becomes a dump of **every** `user-db` doc — a hand-kept type list already dropped collections silently once (GH-163); dumping everything kills the bug class, not the instance. Provision-seed does not use it (push-seed above).

Reaper (systemd timer, mirrors `learning-app-backup-db.timer`) reclaims orphans (a `userdb` abandoned when a device adopts another identity); refund/lapsed-subscription reaping arrives with payment in phase 2. Deferred during the invite phase — blast radius = minted invites, and the column/timer are trivial to add when population grows.

## Consequences

- Default path makes nothing server-side → drive-by `claim` abuse has nothing to make; only invited devices provision.
- Sybil bounded by minted invites (operator-controlled) → safe for closed testing + dogfood, no payment infra.
- App stays anonymous — invite is a bearer token, no PII, no account/email.
- One bearer token = the whole identity; one thing to save/share/back up. Lose it = lose the account — the saved key is the recovery.
- The auth surface shrinks hard: no `password` column, no argon2, no `sessions` table, no `/api/identity/auth`, no Ring session middleware. `/auth/check` does one sha256 lookup; that's the entire per-request auth. Fewer moving parts than what was on the branch.
- Adopting a key on more devices is free (no gate); only minting an account is gated. One invite → one account → many devices, all sharing the token.
- Merge-on-adopt unions two local datasets: vocab dedups for free via content-addressed ids (ADR-0008, shipped); collections (mutable name) can still duplicate-by-value → post-merge concern, acceptable at invite scale.
- The token never reaches the server as part of a URL, so it stays out of access logs and `Referer` headers; it is sent in a request body or the cookie.
- The token is a long-lived bearer secret — in links, browser history, QR screenshots, IndexedDB (`device-db`), and a non-HttpOnly cookie. The cookie can't be HttpOnly because the client both sets it and must read the token to build the QR; this loses nothing, since the token already lives JS-readable in `device-db` (XSS reads it there regardless). Client-composed share only; rotation is the leak remedy.
- No per-device logout (see Decision); rotation is the only revocation, all-or-nothing. Accepted for a personal app; leaf-token revocation can be added later by splitting the token hash into its own one-to-many table.
- Forward-compatible: payment is a drop-in phase-2 grant source; the gate abstraction is unchanged, so no rework when it lands.
- Free path = single-device local-only; multi-device/recovery/backup behind an invite. No-backup-until-granted risk → onboarding nudge, else lost/reset device = silent total data loss.
- Invites must be single-use + unguessable + revocable; a leaked/shared invite costs at most the burns minted.
- Reaper deferred (invite phase); orphans accumulate at most one per cross-account adoption — operator-visible scale.
- Deferred — real payment now: no proven demand, fees/infra/compliance premature; revisit when demand is obvious, the gate slot is kept.
- Rejected — PoW (compute-priced, adds solver/WASM/calibration, needless under controlled invites); rate-limiting alone (bounds inflow into an immortal stock); permanent identity/email anchors (contradict anonymity, beaten cheap by catch-all domains + disposable inboxes); `{id, secret}` + argon2 + sessions (password-login machinery on a high-entropy key — sha256 bearer token is equally secure and far simpler); per-device revocation (illusory unless devices hold non-re-mintable leaf tokens — disproportionate machinery here).
- Implementation — invite mint/verify, provision + token mint, `users.token_sha256`, cookie-carried token, adopt/merge, export dump — deferred to an OpenSpec change referencing this ADR.
