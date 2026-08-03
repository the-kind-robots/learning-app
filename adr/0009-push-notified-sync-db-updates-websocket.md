# 0009. Push-notified sync via _db_updates + WebSocket

- Status: proposed
- Date: 2026-06-09

## Context

ADR-0007 ships trigger-driven one-shot sync with a conscious gap: no real-time while two devices are open at once. Device B sees A's change only on its next pull (page entry / online event). Closing the gap means telling B "your db changed" without per-device held CouchDB feeds — N continuous `_changes` feeds per online device is exactly the cost 0007 removed.

CouchDB has a global fan-in for this: `_db_updates` — **one** feed for the whole node, emitting `{db_name, type}` on any database write. Admin-only, so it must terminate in the backend, not the browser. With `global_changes` enabled it is durable and `since`-resumable; without, it is transient (missed events while the consumer is down are gone). Idle client sockets are cheap on an event-driven server (C10k) — the per-db feed wall was CouchDB's, not TCP's.

Web Push was examined and rejected: built for user-visible notifications (`userVisibleOnly`), silent push is throttled/unreliable and iOS-fragile — semantically the wrong tool for a silent "go pull" signal.

## Decision

One fan-in, own fan-out. The backend holds a single `_db_updates` longpoll loop against CouchDB (localhost, admin creds) and fans out over WebSockets it owns.

- **Fan-in**: a backend component loops `GET /_db_updates?feed=longpoll&since=<last>`, tracking `since` in memory. Longpoll over EventSource/continuous: trivial to consume from Clojure (one blocking request per wake), and it's a single localhost connection — streaming buys nothing. Filter events to `userdb-*`; map `userdb-<id>` → account id.
- **Fan-out**: an http-kit WebSocket endpoint (native `as-channel`). Handshake authenticates via the existing session cookie; an atom registry `account-id → #{channels}` adds on open, removes on close. On a `_db_updates` event, send a tiny poke (no payload) to that account's channels. No payload → nothing secret in the message, no per-doc fan-out logic; the client pulls through the normal path.
- **Client**: a system component opens the socket while the app is foregrounded (visibility API) and closes it on hidden — the radio sleeps in background, battery cost bounded by foreground use. On poke: dispatch `[:effect/sync-pull]` — the entire 0007 pull path (one-shot pass, conflict resolution, `:page/load` reload) is reused unchanged. On reconnect: one pull, covering pokes missed while disconnected.
- **0007 stays as the fallback baseline**: page-entry and `online` pulls cover socket-down windows and `global_changes`-off gaps; the local-changes push side is untouched (the writer needs no notification).
- **Infra**: nginx proxies the ws path with `Upgrade`/`Connection` headers and a long `proxy_read_timeout`; enable `global_changes` in CouchDB for durability (optional at first — reconnect-pull covers transience).

Cost shape: one held CouchDB feed total (not per device) + idle ws sockets ∝ foregrounded clients — the cheap kind of held connection.

## Consequences

- Two open devices converge in seconds: A writes → throttled push → CouchDB → `_db_updates` → poke → B pulls. The 0007 gap closes.
- Server adds two small components (fan-in loop, ws registry) and one nginx location; client adds one foreground-gated socket component. No schema, no new auth — the session cookie gates the handshake.
- The poke carries nothing; a hijacked socket learns only "something changed". Auth failures close the socket at handshake.
- In-memory registry and `since` mean a backend restart drops sockets and may miss events — covered by client reconnect-pull; no persistence needed until `global_changes` is enabled.
- The own-poke echo is tolerated: A's push triggers a poke back to A, whose pull finds nothing (checkpoints converge) — same settled-echo class as 0007's local-feed echo. Server-side coalescing (throttle pokes per account) is an optional refinement.
- Scale ceiling moves from "held CouchDB feeds per device" to "ws connections per foregrounded client" — far cheaper, single-node fine for the invite-phase population (ADR-0006).
- Rejected: per-db `_changes`/EventSource feeds to clients (rebuilds the 0007 wall); Web Push (wrong tool, above); polling-while-foreground (wakes the radio on a timer for nothing — the poke wakes it only when there is data).
- Whether the `online`-event pull stays or folds into reconnect-pull is decided at implementation (left open in the 0007 change).
- Implementation — fan-in loop, ws endpoint + registry, client socket component, nginx — deferred to an OpenSpec change referencing this ADR.
