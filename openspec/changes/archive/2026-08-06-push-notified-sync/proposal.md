# Push-notified sync

## Why

Trigger-driven pulls (ADR-0007) leave one gap: a device learns about remote writes only when the user navigates or reconnects. Pairing shows it worst — the old device sits on the QR dialog until something forces a pull. ADR-0009 closes the gap with server push: one CouchDB `_db_updates` longpoll per node fans in, payload-less pokes fan out over per-account WebSockets, and a poked client pulls through its normal path.

## What changes

- Backend `push` namespace: a single daemon longpoll loop against `_db_updates` (fan-in), an account → channels registry, and a `"1"` poke to every channel of an account whose `userdb-N` was updated. The loop lives with the HTTP server: `start-server!` starts it, `stop-server!` stops it.
- `GET /api/sync/updates`: cookie-authenticated WebSocket upgrade; unauthenticated upgrades are refused with 401. A poke carries nothing, so a hijacked socket learns only "something changed".
- Client `connect-push!`: holds the socket while the page is visible, closes it when hidden, reconnects flat every 5s while visible. `on-poke` runs on every (re)connect and on every message — one pull covers whatever was missed while the socket was down. A poke also closes a waiting pairing dialog: the first poke is how the old device learns pairing worked.
- `lib/db` gains clj-only `db-updates` (one longpoll turn). Production: postinst creates `_global_changes`, nginx upgrades `/api/sync/updates` with a 1h read timeout.

## Impact

New `src/backend/push.clj` + tests; `core.clj` route and server lifecycle; `sync.cljs` socket; `main.cljs` `:app/sync` wiring; `lib/db/src/db.cljc`; `infra/production` postinst + nginx. References ADR-0009; builds on ADR-0007 triggers.
