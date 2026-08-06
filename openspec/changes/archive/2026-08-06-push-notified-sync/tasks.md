# Tasks

- [x] 1. `db/db-updates` in lib/db (longpoll GET `_db_updates`, admin conn)
- [x] 2. Backend `push`: fan-in loop, subscribe/unsubscribe registry, poke fan-out; lifecycle in `start-server!`/`stop-server!`
- [x] 3. `GET /api/sync/updates` — cookie-authed `as-channel` upgrade, 401 otherwise
- [x] 4. Client `connect-push!`: visibility-gated socket, pull on open and on poke, flat 5s reconnect; `:app/sync` connects when the device has an account
- [x] 5. Production: `_global_changes` in postinst, WebSocket location in nginx
- [x] 6. Tests: userdb name parsing; pokes reach only subscribed accounts, deduped per turn, "deleted" ignored; unsubscribe clears empty accounts
- [x] 7. Live proof on the stand: two headless devices on one account — a word added on A appears on idle B in ~1s via poke, no navigation
