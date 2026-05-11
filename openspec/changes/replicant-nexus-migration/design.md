## Context

The app runs entirely inside a Service Worker: every UI interaction is a `fetch` event handled by a ring/reitit handler that reads PouchDB, renders hiccup to an HTML string, and returns an HTTP response for htmx to swap into the DOM. This creates unavoidable cross-context round-trips for every keystroke and tap, which the user experiences as lag.

The architecture also blocks the SQLite dictionary migration: `FileSystemSyncAccessHandle` (required by `opfs-sahpool`) is Dedicated-Worker-only; `new Worker()` is unavailable inside a Service Worker. There is no workaround within the current single-SW model.

Current file shape:
- `sw.cljs` — SW lifecycle + all app routing and rendering
- `application.cljs` — reitit HTTP routes, ring-style handlers
- `views/*`, `presenter/*`, `domain/*` — hiccup / pure logic (reusable as-is)
- `dictionary_sqlite.cljs` — SQLite shim (written but non-functional in SW context)

## Goals / Non-Goals

**Goals:**
- Move all application rendering and state to the main thread (zero htmx round-trips for UI interactions)
- Enable a Dedicated Worker for SQLite (`opfs-sahpool`) spawned from the main thread
- Preserve `domain/`, `presenter/`, views hiccup structure, `vocabulary.cljs`, `lesson.cljs`, `dbs.cljs`, `retention.cljs`, `tasks.cljs` bodies unchanged
- Collapse the SW to a passive cache layer (precache + network-first), eliminating its role as an app server
- Remove htmx from the runtime

**Non-Goals:**
- Changing PouchDB/CouchDB sync protocol or data model
- Migrating user data storage away from PouchDB
- Incremental in-place migration (htmx and Replicant cannot safely co-own the same DOM tree)
- Server-side changes beyond what is already landed (manifest endpoint, dictionary route)

## Decisions

### Replicant for rendering

Replicant takes hiccup data and diffs it against the DOM — no component classes, no hooks, no framework magic. The existing `views/*` functions return hiccup and continue doing so; the delivery mechanism changes from "HTML string over HTTP" to "DOM diff in-process."

**Alternatives considered:** re-frame (too much ceremony for this codebase size), rum (React overhead and lifecycle complexity).

### Nexus for actions and effects

Nexus provides a data-driven action/effect dispatch loop: actions are plain maps, effect handlers are registered functions, state writes happen in a single atom update. The existing ring route handlers translate almost 1:1 into Nexus effect handlers — the body stays, only the call site changes.

`hx-get="/lesson"` → `:on {:click [:action/start-lesson]}`  
Effect handler calls the existing `lesson/start!` / PouchDB functions and writes result into state.

**Alternatives considered:** re-frame (shares the atom model but heavier); raw `core.async` (too low-level, loses the data-orientation).

### Page-slice state shape

```clojure
{:page    :lesson          ; current page identifier
 :data    {…page data…}    ; output of the page's loader effect
 :modal   nil              ; nil or {:type :token-info, :data {…}}
 :toast   nil}             ; nil or {:message "…", :level :error}
```

The `:data` map is the direct equivalent of what a reitit route handler returned as the hiccup context. No normalisation, no entity store. Each navigation action runs a loader effect that replaces `:data` entirely.

**Why not normalised entities:** No current feature requires cross-page caching or optimistic updates. Promoting later is straightforward; premature normalisation adds indirection with no payoff here.

### Dedicated Worker for SQLite

`dictionary_sqlite.cljs` moves into a `:dictionary-worker` shadow-cljs module (`:web-worker true`). Main thread holds a `MessageChannel` port and wraps queries in promise-based RPC (request-id map). The `suggest` function in `dictionary_worker_rpc.cljs` returns a promise identical in shape to the old `dictionary/suggest`.

No change to the `suggest` SQL or result shape — callers in `application.cljs` (now a Nexus effect) use the same `p/let` pattern.

### Hard cutover — no bridge

Replicant and htmx cannot safely share a DOM subtree: htmx OOB swaps landing inside a Replicant-managed node will be overwritten on the next render. The migration is a feature freeze followed by a single cutover commit, not an incremental path-by-path migration.

The codebase has ~10 routes and ~3 000 lines of ClojureScript. A hard cutover is achievable in 1–2 weeks.

### SW stripped to passive cache

After migration the SW retains only:
1. `install` — precache static assets
2. `activate` — `clients.claim`
3. `fetch` — precache hit or network-first for everything else

All imports of `application`, `dictionary*`, `tasks`, `lesson`, `vocabulary`, `db-migrations` are removed from `sw.cljs`. The SW build shrinks to ~50 lines.

## Risks / Trade-offs

- **Background tasks die with the tab.** `tasks/start!` moves to main-thread boot; the task loop stops when the last tab closes. For this app (tasks are triggered by user action in-session) this is acceptable. If true background execution is needed later, a tasks loop in the stripped SW can be added back.
- **First-paint shift.** Today the SW returns a complete HTML fragment; after migration the app shell is static and Replicant renders the first page. The loading splash already handles this; no user-visible regression expected.
- **SQLite worker cold-start adds ~200–500 ms on first use.** Dictionary suggest falls back to empty results while the worker initialises. Acceptable — the PouchDB dictionary path is retained as a fallback behind the rollout flag until the worker is confirmed stable.
- **Nexus API surface.** The Nexus library API must be reviewed against its README before the action registry is written, to avoid renaming mid-implementation.
- **htmx removal touches every view file.** Each `hx-*` attribute becomes a Nexus action or is deleted. This is mechanical but voluminous; lesson view is the hardest (multi-region OOB swaps → single state write).

## Migration Plan

1. **Split the build** — add `:app` (browser) and `:dictionary-worker` (web-worker) modules to `shadow-cljs.edn`; strip SW to passive cache; confirm `:sw` compiles without app imports
2. **Skeleton boot** — `main.cljs` mounts Replicant on `#app`, wires Nexus with a no-op action, calls `db-migrations/ensure-migrated!` + `tasks/start!`
3. **SQLite worker RPC** — move `dictionary_sqlite.cljs` into worker module; expose `suggest` over postMessage; verify from browser console before any UI integration
4. **Port `/home`** — smallest route, single PouchDB read; establishes the loader→state→render pattern
5. **Port `/words`** — proves form submission, validation, pagination
6. **Port `/lesson`** — hardest; multi-region OOB → single state atom write; popover becomes `:modal` slice
7. **Delete htmx** — remove script tags, precache entries, all `hx-*` from views; trim app shell
8. **Rollout flag cleanup** — after SQLite worker is stable, remove PouchDB dictionary fallback path

Rollback: the previous SW-based app is a single commit revert; no data migration is involved.
