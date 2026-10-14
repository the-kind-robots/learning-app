## Context

In-force ADRs (none superseded): ADR-0006 (this change's decision), ADR-0007 (sync triggers — shipped), ADR-0008 (content-addressed vocab ids — shipped). This design implements ADR-0006 phase 1.

Current state: the shipped feature provisions eagerly (`/api/identity/claim` → immediate `userdb`), identity is `{:id, :secret}` with an argon2 password verified at a `/api/identity/auth` login that issues a DB-backed Ring session, pairing destroys the local `user-db` unconditionally, recovery uses one-time server tokens.

## Goals / Non-Goals

**Goals:**
- No server resource until a user opts in with a valid invite.
- One opaque bearer token in the user's hands, on every transport (QR, link).
- Key entry adopts an account by merging local data, wiping only on a cross-account switch.
- Export covers every synced doc type, permanently.

**Non-Goals:**
- Payment (ADR-0006 phase 2) — only the pluggable gate shape is preserved.
- Per-device logout — illusory without non-re-mintable leaf tokens; rotation is the only revocation.
- Reaper + `last_synced_at` — deferred; orphan scale during the invite phase is operator-visible.
- Collection cross-device de-duplication — flagged as an open question.

## Decisions

- **Identity = one high-entropy bearer token, not `{id, secret}` + argon2 + session.** The server stores `sha256(token) → account` inline on the `users` row; the token is presented as a cookie and validated per request. Rationale: argon2 exists to slow brute force on low-entropy human passwords; a 160-bit random token is infeasible to brute-force, so sha256 is the right hash (same as `grants`). The session table existed only to avoid argon2-per-request — drop argon2 and the session disappears with it. Alternative (keep the password/session apparatus) rejected as cargo-culted machinery on an anonymous high-entropy key.
- **Single `users` table holds the credential.** `users (id INTEGER PK, token_sha256 TEXT UNIQUE, created_at)`. The account id is the single source of truth, derived from the token; the token hash sits on the same row, so the two cannot diverge. One token per account (rotation = `UPDATE token_sha256`); a separate one-to-many `credentials` table was considered and dropped as premature flexibility (it duplicated the id across two tables for leaf-token/rotation-overlap features we do not need).
- **Token = 160-bit URL-safe random (`random-token`).** `SecureRandom` → 20 bytes → 40 hex chars. Used for both account tokens and grant tokens, replacing the cargo-culted `(str (random-uuid) (random-uuid))`. No dashes, fixed length, QR-safe.
- **Grants = own table, borrowed mechanics.** `grants (sha256 PK, created_at, expires_at, used_at NULL)` — a grant pre-dates any account, so no user reference. Named for the gate slot, not the phase-1 source: a phase-2 payment mints rows in the same table. Verify+burn atomically (`UPDATE … WHERE used_at IS NULL AND expires_at > ? RETURNING`). Mint = REPL function returning the plaintext token; no admin endpoint.
- **Provision endpoint.** `POST /api/identity/provision {invite}` → verify + burn invite → mint token, INSERT the account row, create + role-secure the `userdb` → return `{id, token}`. Called by the boot hook on an `#invite=` link; a device that already has an account ignores the invite (no accidental second account).
- **Account endpoint for adopt.** The key carries only the token, so a device adopting it learns its account id from `POST /api/identity/account` — the token goes in the body, not the cookie, so the device can find out whether the key is valid and whose it is *before* making it this device's identity. An invalid key writes no cookie, saves no identity and touches no data.
- **Credentials ride in the URL fragment, never the query.** `#key=`, `#invite=`. A fragment is not part of the request: with a query string the token landed in nginx's access log, confirmed by reading it there, and would ride along in `Referer` headers. The router's redirect of the unmatched `/` uses `replace-state`, so the fragment leaves the address bar and the history entry with it — a path with no route is a redirect, not a visit.
- **`/auth/check` = the entire per-request auth.** Reads the `sprecha-token` cookie, `sha256` lookup → account id, emits `X-Auth-UserName/Roles/Token` for the CouchDB proxy, else 401. One sha256 + lookup; this is what the removed session machinery is replaced by.
- **Cookie is a client-set, derived cache of the token.** `use-identity!` writes `sprecha-token=<token>; path=/; SameSite=Lax; Secure` from `device-db`; not HttpOnly (the client owns it and builds the QR from it; the token already lives JS-readable in `device-db`, so HttpOnly buys nothing). No `Max-Age` — a session cookie rebuilt every boot. Rationale: in an offline-first PWA the cookie is the fragile store (Safari ITP caps JS cookies at 7 days; storage-pressure eviction), while `navigator.storage.persist()`-protected IndexedDB is durable. So the token's home is `device-db`, and the cookie is re-derived offline on each boot — eviction of the cookie is a non-event. The server never issues `Set-Cookie` (rejected: it would bet on cookie durability and need a network re-establish when the cookie is evicted).
- **Persistent storage.** A `:storage/persistent` boot component calls `navigator.storage.persist()` to exempt `device-db` (and all PouchDB stores) from automatic eviction. The grant is environmental (browsers gate it on engagement / install); requesting it is the ceiling the standard API offers.
- **Seed via first-sync push, not a separate ingest.** The provisioning device replicates its existing `user-db` into the fresh `userdb` on the next 0007 sync trigger. No payload upload at provision.
- **Adopt/merge.** `check-incoming-auth!` handles one `#key=<token>` param: set the cookie → `account-id!`; nil (401) → invalid key, bail with no save and no wipe; else compare the server-returned id to the stored one → no stored / same id → keep local `user-db` (merge: replication unions local up, account down); different id → destroy local `user-db`, then save. The server id is authoritative (no client-parsed claim). A bad key that clobbers the cookie self-heals: `start!` (later in boot) re-sets the cookie from the stored `device-db` identity.
- **Export = full dump.** `export-data!` returns every `user-db` doc (minus `_rev`), schema 2. `import-data!` dispatches by `:type`: vocab → LWW by `:modified-at`, anything else → insert-if-absent. Accepts schema 1 and 2. A hand-kept type list already dropped collections silently (GH-163); the dump kills the class. The manual export/import UI is removed; the functions stay for cold restore / REPL.
- **Local-first start.** `sync/start!` reads the stored identity; present → `use-identity!` + triggers; absent → inert `{:sync/pull! (constantly nil)}` without any network call. `ensure-identity!`/`claim!`/`auth!` die. The 0007 wiring (capabilities, triggers, pull path) is untouched.
- **No manual entry — links and QR only.** The invite is redeemed by opening an `#invite=` link; an account is adopted by opening a `#key=` link or scanning the pairing QR (same link). Both handled by the boot hook (`check-incoming-auth!`) — one code path, no prompt dialogs. The sync menu keeps its two items (pairing QR, recovery link), now carrying the token. Phase-2 payment redirects fit the `#invite=` door unchanged.

## Risks / Trade-offs

- Token leak = full account access → 160-bit secret, client-side transport only, rotation as remedy; the QR flow already carried the raw credential, so exposure does not widen.
- No per-device logout → accepted for a personal app; deleting one device's cookie is theater while the device holds a re-presentable key. Rotation invalidates all devices at once.
- `persist()` may be denied (environmental) → `device-db` stays best-effort in low-engagement contexts; the cookie-rebuild design tolerates eviction, and installed PWAs are typically granted persistence.
- Cross-device merge can duplicate collections (mutable name; vocab is solved by ADR-0008) → accepted at invite scale, post-merge cleanup manual; open question.
- Wrong merge direction could wipe data → wipe only on a confirmed account-id mismatch after the token validates against the server; default is merge.
- Invite leak/guess → single-use + 160-bit + revocable (delete the row); blast radius = minted invites.
- No server backup until provision → onboarding nudge toward saving the key early.

## Migration Plan

The feature only just merged and has no real users; existing eagerly-created `userdb`s are dev/test artifacts. Plan: drop existing `userdb`s, recreate `app.db` from the new schema (single `users`, `grants`, no `sessions`/`recovery_tokens`), ship local-first + invite gate, mint one operator invite to self-provision. Rollback: re-enable the `claim` route (kept in git history) if invite provisioning blocks testing.

## Open Questions

- Collection cross-device de-duplication: name is mutable so it can't key the doc — needs a post-merge dedup pass or a stable surrogate id. Out of scope here; revisit.
- Token rotation endpoint: in this change or when first needed? Leaning minimal — ship the gate first, rotation lands with the first leak concern.
- Phase-2 payment shape (one-time vs subscription) — deferred, does not constrain this change.
