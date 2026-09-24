# Client

ClojureScript SPA compiled by shadow-cljs. Runs entirely in the browser as a PWA. The dictionary is a SQLite file in OPFS, queried through a Web Worker; the learner's data lives in PouchDB and replicates to CouchDB once the device has an account.

## Stack

| Concern           | Library / API                                   |
|-------------------|-------------------------------------------------|
| Build             | shadow-cljs                                     |
| UI / rendering    | Replicant                                       |
| State / effects   | Nexus                                           |
| Routing           | reitit                                          |
| Client dictionary | SQLite WASM + OPFS (Web Worker)                 |
| User data         | PouchDB (`lib/db`), replicated to CouchDB       |
| Logging           | glogi                                           |

## Source layout

```
src/client/
├── main.cljs                 # Entry point: declares the system and starts it
├── application.cljs          # Nexus interceptors, app-wide effects, shell view, render!
├── application/presenter.cljs# Shell props (sync entry, install prompt, build mark)
├── runtime/system.cljs       # Lifecycle manager: orders components by :requires/:after
├── logging.cljs              # glogi configuration
├── instrumentation.cljs      # Dev-only metrics (window.__metrics); gone in release
├── build_identity.cljs       # Dev build stamp (commit + time); empty in release
├── service_worker.cljs       # Registers sw.js, announces and takes new builds (ADR-0014)
├── sync.cljs                 # user-db ↔ CouchDB replication, LWW vocab conflicts, pairing
├── tasks.cljs                # Background task queue stored in device-db
├── db_migrations.cljs        # One-time data migrations (the local-db split)
│
├── db/                       # Storage engine
│   ├── pouch.cljs            # PouchDB: databases, indexes, views, replication
│   └── sqlite.cljs           # Page side of the dictionary worker protocol
│
├── domain/                   # Pure business logic (no side effects)
│   ├── collections.cljs      # Collection names and folders (ADR-0013)
│   ├── lesson.cljs           # Lesson logic
│   ├── phrase.cljs           # Phrases: vocabulary documents with :kind "phrase"
│   ├── retention.cljs        # Retention calculations
│   └── vocabulary.cljs       # Vocabulary domain model
│
├── ports/                    # Capabilities the system hands to use-cases and pages
│   ├── backup.cljs  clock.cljs  collections.cljs  dictionary.cljs  examples.cljs
│   └── lessons.cljs  navigation.cljs  reviews.cljs  task_queue.cljs  words.cljs
│
├── adapters/                 # Repositories: own a document type, speak domain outward
│   ├── repository.cljs       # Shared edge: stored doc ↔ entity keyed by :id
│   ├── words.cljs  reviews.cljs  collections.cljs     # user-db
│   ├── lessons.cljs  examples.cljs                    # device-db
│   ├── dictionary.cljs       # SQL through the worker proxy
│   ├── identity.cljs         # Device identity (account id + token) in device-db
│   ├── data_export.cljs      # Export/import every user-db document
│   └── active_collection.cljs# Active collection id in localStorage
│
├── use_cases/                # Orchestrate domain + ports for a feature
│   ├── collections.cljs  examples.cljs  lesson.cljs  vocabulary.cljs
│
├── pages/                    # Screens: actions, effects, presenter, view
│   ├── home/                 # Search, autocomplete, add word
│   ├── lesson/               # Lesson card, scoring, token popover
│   ├── words/                # Vocabulary list
│   └── collections/          # Collections screen
│
└── install_guide/            # PWA install prompt (core + view)
```

Shared code the client also compiles: `lib/db/src/db.cljc` (the PouchDB/CouchDB wrapper) and `src/shared/` (`auth`, `userdb`, `utils`, `hiccup`).

## Architecture

**Replicant + Nexus** — Replicant renders hiccup → DOM from immutable state. Nexus manages async effects and state transitions. Actions (pure) → effects (async/IO) → state updates.

**System** — `main.cljs` declares components (`:db/pouch`, `:db/sqlite`, `:sync/identity`, `:port/*`, …) and `runtime/system.cljs` starts them in dependency order. Ports are the capabilities; use-cases and pages reach storage only through them.

**Layering** — the engine (`db`, `db.pouch`, `db.sqlite`, `db-migrations`, `sync`, `tasks`) knows documents, databases and replication. Adapters own their document type and speak domain outward. Use-cases, pages and domain never see a storage name. `test/client/layering_test.cljs` checks this against the source tree.

**Dictionary worker** — SQLite WASM runs in a dedicated Web Worker (`resources/public/js/sqlite3-worker.js` + `sqlite3-dictionary.js`). The worker fetches `/dictionary/manifest` and the hashed `dict.*.sqlite` file into the OPFS pool. `ports/dictionary.cljs` exposes the app-facing capability; `adapters/dictionary.cljs` executes SQL through the worker proxy. Never query the dictionary from the main thread directly.

Every tab has a worker, but the dictionary belongs to the tab being typed into: `opfs-sahpool` admits a single holder, so a tab takes the `sqlite-opfs-sahpool` Web Lock when it comes to the foreground — on screen *and* holding the keyboard — and gives it back when it leaves (`sqlite3-dictionary.js`, ADR-0012). Visibility alone would not do: two tabs can be visible side by side and only one is being used. A tab without a turn answers the queries asked of it with no completions and keeps nothing, so never gate a call on readiness — just ask, and read `:dictionary/ready?` if you have to say *why* a list is empty (#312).

**User data** — PouchDB, split in two databases. Each adapter's schema names its database:

- `user-db` — words, reviews, collections. What follows the learner across devices: replicated to the account's CouchDB database `userdb-N` through `/db/` (ADR-0006). `sync.cljs` pushes on local changes (throttled) and pulls on data-page entry; vocab conflicts resolve last-writer-wins by `:modified-at`. A device without an account is local-only and makes no network call.
- `device-db` — lessons, examples, tasks, the device identity. Never replicated.

`db_migrations.cljs` runs before `db.pouch` opens the databases; it once split the old single `local-db` into these two.

**Service worker** — plain JS, `resources/public/js/sw.js`; does not run ClojureScript. The backend serves it at `/js/app/sw.js` and prepends `SW_VERSION` (the cache bucket name) and `PRECACHE_URLS` (`src/backend/core.clj`, `service-worker-handler`). It never skips waiting on its own; `service_worker.cljs` announces a new build and takes it only when asked (ADR-0014).

## Building

```bash
npx shadow-cljs watch app          # dev build + hot reload
npx shadow-cljs release app        # production build
npx shadow-cljs compile node-test && node target/node-tests.js   # client tests
```

Client test namespaces live in `test/client/` and run in Node. Browser specs are Playwright, in `test/browser/` (`npm run test:browser`).
