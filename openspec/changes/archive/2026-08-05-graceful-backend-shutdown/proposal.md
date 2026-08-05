# Graceful shutdown of the backend

## Why

systemd stop sends SIGTERM and the JVM dies mid-request: the listening socket vanishes without draining, in-flight responses are cut, and whatever SQLite connection a request held is severed instead of closed. Nothing in the process knew shutdown existed.

## What changes

- A JVM shutdown hook stops the http-kit server via `server-stop!` with a drain timeout: the listening socket closes first, in-flight requests get up to 5 s to finish. Draining is also what closes SQLite — connections are per-request (`with-open` in `on-connection`), so the last response closes the last one.
- The hook is registered once per JVM (`defonce`), so the dev reload path cannot register a duplicate; `restart-server!` now delegates to `stop-server!`, which owns the drain timeout.
- A test proves the drain: an in-flight request finishes with 200 while `stop-server!` runs, the socket refuses afterwards, the handle is released.

## Impact

New capability: `backend-operations`. Files: `src/backend/core.clj`, `test/backend/core_test.clj`.
