## 1. Backend: schema + invites

- [x] 1.1 Single `users (id, token_sha256 UNIQUE, created_at)` in `initial-setup.sql`; drop `sessions` and `recovery_tokens` and the `name`/`password` columns
- [x] 1.2 `grants` table (`sha256` PK, `created_at`, `expires_at`, `used_at` NULL)
- [x] 1.3 `random-token` (160-bit hex, `SecureRandom`) shared by accounts and grants; replaces `(str (random-uuid) …)`
- [x] 1.4 REPL mint fn (`mint-grant!`): generate token, store sha256 + expiry, return plaintext once

## 2. Backend: token auth + provision + cleanup

- [x] 2.1 `create-account!`: mint token, INSERT `users (token_sha256)`, create + role-secure the `userdb`, return `{id, token}`
- [x] 2.2 `POST /api/identity/provision {invite}`: verify + burn invite atomically → `create-account!` → `{id, token}`
- [x] 2.3 `POST /api/identity/account`: token in body → `{id}` or 401 (adopt id-resolution + token validation)
- [x] 2.4 `/auth/check`: read `sprecha-token` cookie → sha256 lookup → CouchDB proxy headers, else 401
- [x] 2.5 Remove `claim`, `auth`, `recovery-token`, `redeem` routes; drop the Ring session middleware + `Sessions` store + argon2; parse cookies via `ring.middleware.cookies`

## 3. Client: token identity

- [x] 3.1 `adapters/identity.cljs`: `use-identity!` (writes the `sprecha-token` cookie, `Secure`), `account-id!` (`POST /account` with the token in the body), `provision!`; drop `auth!`/`claim!`
- [x] 3.2 `sync.cljs` `load`/`save-identity!` store `{:user-id :token}` in `device-db`
- [x] 3.3 `application.cljs` `account-key-url` carries the token directly (`/#key=<token>`); QR + recovery link use it

## 4. Client: local-first start + durable storage

- [x] 4.1 `sync/start!`: stored identity → `use-identity!` + triggers; absent → inert `{:sync/pull! (constantly nil)}`, no network call
- [x] 4.2 `main.cljs` `:storage/persistent` component requests `navigator.storage.persist()` at boot

## 5. Client: adopt / provision on boot

- [x] 5.1 `check-incoming-auth!` `#key=<token>`: set cookie → `account-id!`; nil → bail (no save/wipe); else compare server id with stored → same/none → keep local db (merge); different → destroy local db, then save
- [x] 5.2 `check-incoming-auth!` `#invite=`: no stored identity → `provision!` + save; already provisioned → ignore (invite not burned)
- [x] 5.3 Remove the `?recover=` and `?id=&secret=` branches

## 6. Export / import

- [x] 6.1 `export-data!` dumps every `user-db` doc (minus `_rev`), schema 2
- [x] 6.2 `import-data!` dispatches by `:type`: vocab LWW, default insert-if-absent; accepts schema 1 and 2
- [x] 6.3 Remove the manual export/import UI (words-page `⋮` menu, its effects and actions); functions stay for cold restore / REPL

## 7. Migration (operator)

- [x] 7.1 Drop dev/test `userdb`s; recreate `app.db` from the new schema; mint one invite and self-provision

## 8. Verify

- [x] 8.1 Fresh launch (cleared site data): zero identity/auth network calls, no server `userdb`; words work locally
- [x] 8.2 Provision: junk invite → 403, no `userdb`; valid → `{id, token}`, burned, `userdb` + role; reuse → 403; `#invite=` link provisions on boot; provisioned device ignores a second invite
- [x] 8.3 Auth: `/auth/check` with cookie → 200 + `X-Auth-*`; `/account` with cookie → `{id}`; both 401 on a bad/absent token
- [x] 8.4 Seed-push: pre-provision local word (`vocab:tisch`, content-addressed) reached `userdb-N` on the next sync pass
- [x] 8.5 Adopt: same-account `#key=` → merge, local data kept; cross-account `#key=` → local wipe + switch; cookie set client-side from JS each time
- [ ] 8.6 Export round-trip: full dump includes collections; double import idempotent (no UI — verify via REPL or unit test)
- [x] 8.7 `npx shadow-cljs compile app` clean (0 warnings) and node tests green (130 tests / 305 assertions)
