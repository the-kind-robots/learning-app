## Why

The shipped feature starts continuous live replication (`{:live true :retry true}`) for every online device. The held feed is not a TCP problem — the specific costs are CouchDB's per-database `_changes` feeds (shards, file descriptors, Erlang processes under db-per-user) and mobile radio battery (an unmeasured hypothesis, tracked separately). Needed freshness is narrow: remote data matters only on screens that display synced data. Per ADR-0007, replication becomes trigger-driven one-shot passes — the baseline under a future `_db_updates` + WebSocket push layer.

## What Changes

- **BREAKING**: Stop permanent live replication. Each pass is a one-shot `db.sync(remote, {live: false})` that completes and closes; the pass resolves vocab conflicts (LWW + translation union, ADR-0008) before reporting done.
- **Push**: the local PouchDB `.changes` feed on `user-db` (an IndexedDB event stream, not a network feed) drives a throttled (3s, leading-edge) pass when online. The db's own change events are the single chokepoint — no hand-listed set of Nexus write effects to drift.
- **Pull**: a pass on entering a data page — words, collections, lesson (route controllers) — and on the `online` event — the online pass runs regardless of page, since it also flushes offline writes. After a pass, the current page reloads its store slice via `:page/load` set in state by the page's show action; pages without it (home, lesson) skip the reload.
- Lesson pulls on entry as well; a running lesson is never rebuilt by it, since `:page/load` is nil there and the post-pass reload is a no-op.
- Offline-first render: entering a data page renders local data immediately and pulls in the background, re-rendering on arrival; rendering never blocks on the network.

## Capabilities

### New Capabilities
- `sync-triggers`: trigger-driven replication — throttled push from the local changes feed, pull on data-page entry and on `online`, state-driven post-pull reload — replacing permanent live replication; offline-first render that never blocks on the network.

### Modified Capabilities
<!-- None. Live replication was never captured in a spec, so trigger-driven replication is introduced fresh under sync-triggers. -->

## Impact

- **Client**: `src/client/sync.cljs` (one-shot `sync-once!` with conflict resolution, throttled push off the local changes feed, expose `:sync/pull!`; `stop!` removed), `src/client/application.cljs` (`:effect/sync-pull`, `:action/reload-page`, pull on words/collections controllers, top-level nil-stripping render fix), `src/client/main.cljs` (`:app/sync` online listener, `:capabilities/sync` wiring), page show actions (`:page/current` + `:page/load`).
- References ADR-0007 (baseline) and ADR-0008 (conflict resolution); independent of ADR-0006.
