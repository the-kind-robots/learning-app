# Client

ClojureScript SPA compiled by shadow-cljs. Runs entirely in the browser as a PWA. The dictionary is stored in SQLite/OPFS and queried via a Web Worker; user progress (lessons, retention) lives in IndexedDB.

## Stack

| Concern           | Library / API                    |
|-------------------|----------------------------------|
| Build             | shadow-cljs                      |
| UI / rendering    | Replicant                        |
| State / effects   | Nexus                            |
| Routing           | reitit                           |
| Client dictionary | SQLite WASM + OPFS (Web Worker) |
| User data         | IndexedDB (via `lib/db`)         |
| Logging           | glogi                            |

## Source layout

```
src/client/
├── main.cljs                 # Shadow-cljs entry point — mounts app
├── application.cljs          # App state init, Nexus setup
├── logging.cljs              # glogi configuration
├── dbs.cljs                  # DB handles (IndexedDB + OPFS)
├── db_migrations.cljs        # IndexedDB schema migrations
├── tasks.cljs                # Async task queue / coordination
│
├── domain/                   # Pure business logic (no side effects)
│   ├── lesson.cljs           # Spaced-repetition lesson logic
│   ├── retention.cljs        # Retention curve calculations
│   └── vocabulary.cljs       # Vocabulary domain model
│
├── pages/                    # Screen-level components (view + actions + effects)
│   ├── home/                 # Home screen (search, autocomplete)
│   ├── lesson/               # Lesson screen (card, scoring)
│   └── words/                # Vocabulary list
│
├── repo/                     # Data access (IndexedDB + dictionary worker)
│   ├── examples.cljs         # Example persistence + fetch task handler
│   └── retention.cljs
│
├── use_cases/                # Orchestrate domain + repo for a feature
│   ├── lesson.cljs
│   └── vocabulary.cljs
│
└── install_guide/            # PWA install prompt UI
```

## Architecture

**Replicant + Nexus** — Replicant renders hiccup → DOM from immutable state. Nexus manages async effects and state transitions. Actions (pure) → effects (async/IO) → state updates.

**Dictionary worker** — SQLite WASM runs in a dedicated Web Worker (`resources/public/`). `ports/dictionary.cljs` exposes the app-facing capability; `adapters/dictionary.cljs` executes SQL through the worker proxy. Never query the dictionary from the main thread directly.

Every tab has a worker, but the dictionary belongs to the tab being typed into: `opfs-sahpool` admits a single holder, so a tab takes the `sqlite-opfs-sahpool` Web Lock when it comes to the foreground — on screen *and* holding the keyboard — and gives it back when it leaves (`sqlite3-dictionary.js`, ADR-0012). Visibility alone would not do: two tabs can be visible side by side and only one is being used. A tab without a turn answers the queries asked of it with no completions and keeps nothing, so never gate a call on readiness — just ask, and read `:dictionary/ready?` if you have to say *why* a list is empty (#312).

**IndexedDB** — lessons, retention scores, and vocabulary lists. Migrations run at startup via `db_migrations.cljs`. `lib/db` macros abstract the IDB API.

**Service worker** — plain JS (`resources/public/`). Caches static assets; does not run ClojureScript.

## Building

```bash
npx shadow-cljs watch app          # dev build + hot reload
npx shadow-cljs release app        # production build
npx shadow-cljs node-test          # run node tests
```

Test namespaces live in `test/client/`. Domain logic tests run in Node; DB/browser tests run in browser-test build.
