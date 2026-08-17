## Why

The dictionary is reachable from one browsing context at a time (#351). Each tab spawns its
own worker, and `sqlite3-worker.js` takes the pool Web Lock with
`ifAvailable: true` and holds it for the worker's whole life. Exactly one tab wins; every
other tab is answered `{type: "error", code: "another-tab-open"}`, which the client only
logs — and release builds log nothing. `adapters.dictionary/completions` then sees
`ready? = false` and returns `[]`, so the user gets a silently empty suggestion list.

Observed on a phone with five tabs open: the OPFS database was fully present (37 MB,
`persisted: true`), so this is neither a download nor a storage failure. Suggestions
returned as soon as the extra tabs were closed.

`opfs-sahpool` takes exclusive access handles, so one context at a time really is the
constraint. The question is only which context, and two measurements settle it.

A `SharedWorker` cannot be the one. On Chrome 144, inside a `SharedWorkerGlobalScope`,
`FileSystemFileHandle.prototype.createSyncAccessHandle` is `undefined`, so
`installOpfsSAHPoolVfs` fails with `Missing required OPFS APIs`; `Worker` is `undefined`
there too, so it cannot spawn a dedicated worker to hold the pool for it.

A backgrounded tab cannot be the one either. Android freezes a backgrounded tab whole — its
worker and its message delivery together — about a minute after it leaves the screen.
Measured with two tabs and one query every 10 s while the tab holding the database sat in
the background: 12 ms, 14, 17, 22, 31, 31, then nothing at all from 60 s on, 14 consecutive
timeouts until it was brought back. The failure is silent: no error, no state change, the
promise never settles.

So the database belongs to the tab being typed into — which is not the same as any tab
that happens to be on screen. Two tabs can be visible at once, split screen or side by
side, and `document.hidden` is false for both; the one to serve is the one with the
keyboard. The condition is `visibilityState === "visible" && hasFocus()`.

## What Changes

- A tab takes the `sqlite-opfs-sahpool` lock when it comes to the foreground, opens the
  database and serves itself. When it leaves the foreground it closes the database, pauses
  the pool and releases the lock. The lock is requested without `ifAvailable`, so a tab that
  does not win waits its turn instead of failing.
- A tab waiting its turn has no dictionary of its own and holds the queries asked of it
  until it has one. Nothing is answered from another tab and nothing crosses a tab boundary.
- The page tells its worker what it is, as one boolean: `visibilitychange`, `focus` and
  `blur` are forwarded, and the current
  state is sent once at startup so a tab that boots in the background never takes a database
  it would be frozen holding.
- Between turns a tab keeps its pool installed and paused. Measured: `unpauseVfs` 3-5 ms and
  open 1-2 ms, against `installOpfsSAHPoolVfs` 13-27 ms plus a manifest fetch. Giving it back
  costs 1 ms, and a whole focus switch ~14 ms.
- The engine is loaded on a tab's first turn rather than at startup, so a tab that never
  comes to the front never pays for it.
- The readiness gate in front of the query goes, and `:dictionary/ready?` goes with it. A
  caller has nothing to decide with it now that an early query is held rather than dropped,
  and the accessor chain behind it — `adapters.dictionary/ready?`, `db.sqlite/ready?`, the
  `:ready?` flag the lifecycle messages wrote, the `:state` entry the component exposed only
  so that flag could be read — has no other reader.
- Removed with it: the `another-tab-open` error and its user-facing message. There is no
  losing tab left to describe.

## Capabilities

### Modified Capabilities

- `sqlite-dictionary-worker`: which browsing context holds the database, and what a context
  without it does with a query.
- `dictionary-storage`: the browser-side requirement naming what opens the OPFS file, and
  what autocomplete does before it is open.
- `client-runtime-dependencies`: the dictionary handle is callable at any point and the port
  exposes no readiness predicate.

### New Capabilities

None.

## Impact

- `resources/public/js/sqlite3-dictionary.js` (new) — the lock, the pool, the turn, and the
  queue of requests made between turns.
- `resources/public/js/sqlite3-worker.js` — reduced to the page-facing host, carrying the
  visibility message through.
- `resources/public/js/sw.js` — the new file joins `PRECACHE_URLS`.
- `src/client/db/sqlite.cljs` — forwards `visibilitychange` and the startup state; `attach`
  split out of `init!` so the protocol can be driven where there is no `Worker`.
- `src/client/adapters/dictionary.cljs`, `src/client/ports/dictionary.cljs`,
  `src/client/pages/home/effects.cljs` — the readiness gate and the accessor behind it.
- `test/client/db/sqlite_test.cljs` (new) and `test/client/pages/home_test.cljs` — the page
  protocol under Node, where there is no worker to spawn.
- `test/browser/dictionary-focus.spec.js` (new) — six scenarios for the rule.
- ADR-0012, `src/client/README.md`, `test/browser/README.md`.

Telemetry (`instrumentation/dictionary-*`, `?telemetry=1`) keeps working: `ready-ms` and the
phase list are unaffected, and per-turn phases (`pause`, `unpause`) report alongside them.

Two tabs visible at once still get one dictionary between them, but it is the one with the
keyboard, and it follows the user as they click from pane to pane. When the browser window
itself is behind something else, no tab holds the database: there is nobody to ask, and the
first tab to come back takes it in ~14 ms.
