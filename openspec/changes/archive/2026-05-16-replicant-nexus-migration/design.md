## Context

The app used to run as a Service Worker app server: UI events triggered htmx requests, the SW handled them with Ring/Reitit-style routes, read PouchDB, rendered HTML, and htmx swapped the DOM. That architecture added latency and made browser-worker ownership awkward for the SQLite dictionary migration.

Current file shape after implementation:
- `src/client/main.cljs` — main-thread boot, router, Replicant render, Nexus dispatch, migrations/tasks, SQLite worker init, SW registration.
- `src/client/application.cljs` — shared Nexus effects/actions/placeholders.
- `src/client/pages/{home,words,lesson}/` — page actions/effects/views/presenters.
- `src/client/repo/*` and `src/client/use_cases/*` — data and domain operations used by effects.
- `resources/public/js/sw.js` — passive static cache Service Worker.
- `resources/public/js/sqlite3-worker.js` — plain JS SQLite/OPFS Dedicated Worker.

## Goals / Non-Goals

**Goals:**
- Move application rendering and state to the main thread.
- Enable a Dedicated Worker for SQLite (`opfs-sahpool`) spawned from the main thread.
- Preserve existing domain/use-case semantics while changing delivery from htmx fragments to Replicant state renders.
- Collapse the SW to a passive cache layer.
- Remove htmx from runtime scripts and page views.

**Non-Goals:**
- Changing PouchDB/CouchDB sync protocol or user/device data model.
- Migrating user data storage away from PouchDB.
- Co-owning DOM with htmx and Replicant.
- Removing backend Ring/Reitit. Backend routes still serve API, dictionary assets, service worker, and app shell.

## Decisions

### Replicant for rendering

Replicant diffs hiccup against the DOM. The former view hiccup survives, but it now lives under `src/client/pages/**/view.cljs` and renders in-process instead of being serialized as HTML responses.

**Alternatives considered:** re-frame (too much ceremony for this codebase size), rum (React overhead and lifecycle complexity).

### Nexus for actions and effects

Nexus provides data-driven action/effect dispatch. UI event attributes are plain data, e.g. `:on {:click [[:action/go-to-lesson]]}`. Effect handlers perform PouchDB/domain work and write results into the shared state atom through `:effect/save`.

### Flat namespaced app state

The implemented state shape uses namespaced flat keys, for example:

```clojure
{:app/page :page/home
 :home/word ""
 :home/suggestions nil
 :words/items [...]
 :lesson/state {...}
 :modal/type nil
 :modal/data nil}
```

This avoids a generic nested `:data` blob while still keeping page ownership clear. Each page loader writes only its page slice plus `:app/page`.

### Main-thread router stays `reitit.frontend`

Client HTTP routing was removed, but `reitit.frontend` remains as a lightweight browser router. Route controllers dispatch page load effects for `/home`, `/words`, and `/lesson`.

### Dedicated SQLite Worker is plain JS

The dictionary worker is `resources/public/js/sqlite3-worker.js`, not a CLJS module. It imports official SQLite WASM assets, installs `opfs-sahpool`, imports/opens the content-addressed SQLite file, and answers generic `db.exec` requests. `src/client/dbs.cljs` manages readiness and request-id Promise resolution.

**Rationale:** Plain JS avoids shadow-cljs/WASM bundling friction and keeps the SQLite synchronous API entirely off the UI thread.

### Hard cutover — no htmx bridge

Replicant and htmx cannot safely share a DOM subtree. htmx OOB swaps inside a Replicant-managed node would be overwritten on the next render. The migration therefore replaced the UI routing/view layer as a hard cutover.

### SW stripped to passive cache

After migration the SW retains only:
1. `install` — precache static assets.
2. `activate` — delete old caches and `clients.claim`.
3. `message` — ping/pong probe.
4. `fetch` — cache-first for listed static assets only; other requests fall through.

The SW does not serve application HTML. The backend default handler returns the app shell for normal navigation.

### Dataspex action-log inspector in dev

`nexus.action-log/inspect` runs under `goog/DEBUG` to make Nexus actions/effects visible during development.

## Risks / Trade-offs

- **Background tasks die with the tab.** `tasks/start!` now runs from main-thread boot. This is acceptable for user-session tasks; true background work can be reintroduced in SW later if needed.
- **First-paint shift.** The backend shell shows a splash until Replicant renders. No SW-rendered HTML fragments remain.
- **SQLite worker readiness.** First import/open can lag; autocomplete skips completions until ready.
- **Main-thread router dependency remains.** Reitit frontend is still present by design; only client HTTP interceptors/sieppari were removed.
- **Browser runtime proof still needed.** Code and OpenSpec validation prove structure, not real-device OPFS/Replicant behavior.

## Migration Plan

1. Split runtime: `:app` browser build becomes the client app; SW becomes plain static JS.
2. Backend default handler serves shell + `/js/app/main.js`.
3. `main.cljs` mounts Replicant, registers Nexus, runs migrations/tasks, starts dictionary worker, registers SW.
4. Port `/home`, `/words`, `/lesson` to page actions/effects/views.
5. Move token-info to `:modal/*` state and `<dialog>` lifecycle hooks.
6. Delete htmx scripts, old views, old client route/interceptor stubs, and obsolete helper JS.
7. Browser-verify app shell, navigation, words CRUD, lesson flow, modal flow, and SQLite autocomplete.

Rollback: revert the migration commits; no user data migration is involved.
