## 1. Find out which context can hold the pool

- [x] 1.1 Probe `createSyncAccessHandle`, `Worker`, `getDirectory` and `navigator.locks` inside a `SharedWorkerGlobalScope`, and record the result in the proposal and ADR-0012.
- [x] 1.2 Measure what a backgrounded holder does over time on a real Android device, and record the numbers.

## 2. Ownership follows visibility

- [x] 2.1 Move the pool, the manifest, the import and `db.exec` out of `resources/public/js/sqlite3-worker.js` into a new `resources/public/js/sqlite3-dictionary.js`.
- [x] 2.2 Take `sqlite-opfs-sahpool` — without `ifAvailable` — when the page reports itself in the foreground, and give it back when it reports itself out of it.
- [x] 2.3 Forward `visibilitychange` from `src/client/db/sqlite.cljs`, and send the current state once at startup so a tab that boots in the background takes nothing.
- [x] 2.4 Hold queries made while this tab has no database; settle them when its turn comes.
- [x] 2.5 Keep the pool installed and paused between turns; load the engine on the first turn rather than at startup.
- [x] 2.6 Add `/js/sqlite3-dictionary.js` to `PRECACHE_URLS` in `resources/public/js/sw.js`.

## 3. The awkward moments

- [x] 3.1 Hidden while the database is still opening: close it and give the lock straight back.
- [x] 3.2 Visible again while the previous turn is still being wound up: request another turn once the wind-up finishes.
- [x] 3.3 Failed to open: pause the pool before releasing the lock, so no other tab is blocked by this one.
- [x] 3.4 Hidden while queued but not yet granted: take nothing when the turn arrives.

## 4. No readiness gate, and no readiness accessor

- [x] 4.1 Drop the gate from `adapters.dictionary/completions` and from the suggest effect in `pages/home/effects.cljs`.
- [x] 4.2 Keep `:dictionary/ready?` on the port as a reading rather than a gate: it is the only thing that separates an empty answer from a missing dictionary (#312).
- [x] 4.3 Split `db.sqlite/attach` out of `init!` so the page protocol can be driven where there is no `Worker`.

## 5. Verification

- [x] 5.1 Measure both handover paths — pool kept and paused, versus installed again — and choose on the numbers.
- [x] 5.2 Node unit tests for the page protocol: id matching, lifecycle messages leaving the response matcher alone, exec resolution and rejection, and a query issued with no readiness message received.
- [x] 5.3 Browser spec: visible tab has it, waiting tab does not, a query asked while waiting comes back empty and is not replayed, the tab that goes away gives it back, rapid switching leaves the visible tab working, all tabs away then one returns, closing the holder releases it.
- [x] 5.4 Node test build and the full browser suite against a local stand with the fixture dictionary.

## 6. Documentation

- [x] 6.1 ADR-0012, superseding ADR-0002.
- [x] 6.2 `src/client/README.md` and `test/browser/README.md`, including what the visibility shim in the spec does and does not prove.
