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
├── application/pages.cljs    # Every page once: path, open action, refresh, view
├── application/view.cljs     # The shell's markup
├── application/sync/         # Joining a device to the sync: menu, QR dialog, link
├── runtime/system.cljs       # Lifecycle manager: orders components by :requires/:after
├── logging.cljs              # glogi configuration
├── instrumentation.cljs      # Dev-only metrics (window.__metrics); gone in release
├── build_identity.cljs       # Dev build stamp (commit + time); empty in release
├── service_worker.cljs       # Registers sw.js; the build mark takes new builds (ADR-0022)
├── db_migrations.cljs        # One-time data migrations (the local-db split)
│
├── db/                       # Storage engine
│   ├── pouch.cljs            # PouchDB: databases, indexes, change feeds, replication
│   └── sqlite.cljs           # Page side of the dictionary worker protocol
│
├── domain/                   # Pure business logic (no side effects)
│   ├── collections.cljs      # Collection names and folders (ADR-0013)
│   ├── examples.cljs         # Which examples a read sees; which pairs of an entry have none
│   ├── lesson.cljs           # Lesson logic
│   ├── phrase.cljs           # Phrases: vocabulary documents with :kind "phrase"
│   ├── retention.cljs        # Retention calculations
│   └── vocabulary.cljs       # Vocabulary domain model
│
├── ports/                    # One port per adapter: the only place that names it
│   ├── browser.cljs          # The page, navigation, install prompt, service worker
│   ├── learner.cljs          # The learner's data, its databases, sync, backup, identity
│   ├── dictionary.cljs  examples_generator.cljs  world.cljs
│
├── adapters/                 # Speak domain outward over the outside world; only ports require them
│   ├── learner.cljs          # The learner's data for use cases: memory to read, every write
│   ├── learner/              # Its parts, over user-db and device-db
│   │   ├── documents.cljs    # Document types (schemas) and doc ↔ entity
│   │   ├── memory.cljs       # The learner's data in memory, a projection of user-db
│   │   ├── loader.cljs       # Loads memory and follows user-db's changes
│   │   ├── snapshot.cljs     # Memory's snapshot in the Cache API
│   │   ├── active_id.cljs    # Active collection id in localStorage
│   │   ├── sync.cljs         # user-db ↔ CouchDB replication, LWW vocab conflicts, pairing
│   │   ├── backup.cljs       # Export/import every user-db document
│   │   ├── identity.cljs     # Device identity (account id + token) in device-db
│   │   └── databases.cljs    # Opens PouchDB, runs the background migrations, asks for persistence
│   ├── browser/              # page.cljs (visible, online), navigation.cljs, install_prompt.cljs, service_worker.cljs
│   ├── dictionary.cljs       # SQL through the worker proxy; opens SQLite
│   ├── examples_generator.cljs  # The backend request for one example
│   └── world.cljs            # Time, timers, chance, a turn for the page
│
├── use_cases/                # Orchestrate domain + ports for a feature
│   ├── collections.cljs  examples.cljs  lesson.cljs  vocabulary.cljs
│
├── pages/                    # Screens: actions, effects, presenter, view
│   ├── home/                 # Search, autocomplete, add word
│   ├── lesson/               # Lesson card, scoring, token popover, tiles element
│   ├── words/                # Vocabulary list
│   └── collections/          # Collections screen
│
└── install_guide/            # PWA install prompt (core + view)
```

Shared code the client also compiles: `lib/db/src/db.cljc` (the PouchDB/CouchDB wrapper) and `src/shared/` (`auth`, `userdb`, `utils`, `hiccup`).

## Architecture

**Replicant + Nexus** — Replicant renders hiccup → DOM from immutable state. Nexus manages async effects and state transitions. Actions (pure) → effects (async/IO) → state updates.

**Web components for UI-only state** — the answer field of a lesson (`pages/lesson/answer_field.cljs`) is a custom element, `<answer-field>`, because how the tiles are arranged is state of the screen, not of the business, and Replicant has no state local to a component. From outside it is a textarea with a twist: the view gives it the attributes of a textarea (`name`, `placeholder`, `maxlength`, `aria-label`, ...), it has a `value`, takes part in its form (`formAssociated`, so `form.elements.answer.value` works) and fires `input`. The twist is a light bulb, shown when the view passes a `correct-answer`: a one-way checkbox in the shadow root that, once on, swaps the textarea for tiles of the answer's words (a bank above the field, the placed tiles in it); the value is then the placed words, otherwise the typed text. Which of textarea, bank and tray shows is the checkbox's state read by CSS sibling selectors; the script only builds and moves the tiles and keeps the value. The element knows no UI text (`bank-label`, `tray-label`, `hint-label` name its parts) and nothing of the store, Nexus or the page's CSS classes; it uses Replicant's string renderer only to write its own skeleton as HTML once (the tiles are made and moved by hand). It is written in ClojureScript with `shadow.cljs.modern/defclass`, its shadow styles in `answer_field.css` next to it (inlined at compile time with `shadow.resource/inline`). How it looks is set from outside: font and colour by inheritance, the field's box, the tiles' gap, the bulb and caret by custom properties (`--answer-field-*`), and the tile through the one part it exposes, `tile` — a set of properties for it would be longer than its rule. All of it is styled in `lesson.css` on `.lesson__input`. Reach for the same technique only when state is UI-only and the app has no use for it.

**System** — `main.cljs` declares one component per port (`:port/world`, `:port/browser`, `:port/learner`, `:port/dictionary`, `:port/examples-generator`) and the application's own (`:app/capabilities`, `:app/render`, `:app/router`, `:examples/backfill`, …), and `runtime/system.cljs` starts them in dependency order. A port's `start!` returns its capability at once, with what it waits for as promises in it; a port needs no dispatch, it offers subscriptions (`:browser/on-route`, `:learner/on-change`, `:learner/on-device-joined`) that `application` hangs its dispatch on once render has started; the learner owns its memory and hands it out as `:learner/memory`, while `:app/state` holds the state of the interface only, and Nexus actions get both in their `state` (`application/state`); the learner adapter orders its own parts (credential, databases, migrations, read, sync, following) with the same `runtime/system.cljs`. Ports are the capabilities; use-cases and pages reach the outside world only through them.

**Layering** — the engine (`db`, `db.pouch`, `db.sqlite`, `db-migrations`) is generic PouchDB and SQLite. Adapters speak domain outward; the data in user-db is one adapter, `adapters/learner/`: `documents` (the document types), `learner` with `learner/memory`, `learner/loader`, `learner/snapshot` and `learner/active-id`, `sync` and `backup`. Only ports require adapters, and an adapter reaches no adapter of another top-level name (`bb check-layering`). Use-cases, pages and domain never see a storage name. Use cases and pages require no adapter: they read memory through the learner capability, whose reads (`:learner/words`, `:learner/collections`, …) are functions of the memory value. A page's presenter maps state to what its view renders, and nothing else: the view consumes presenter output, while actions, effects and use cases never require a presenter — a function or constant they need lives with them, or in `domain/`. `.clj-kondo/config.edn` (`:ns-groups` + `:discouraged-namespace`) enforces the require rules.

**Dictionary worker** — SQLite WASM runs in a dedicated Web Worker (`resources/public/js/sqlite3-worker.js` + `sqlite3-dictionary.js`). The worker fetches `/dictionary/manifest` and the hashed `dict.*.sqlite` file into the OPFS pool. `ports/dictionary.cljs` exposes the app-facing capability; `adapters/dictionary.cljs` executes SQL through the worker proxy. Never query the dictionary from the main thread directly.

Every tab has a worker, but the dictionary belongs to the tab being typed into: `opfs-sahpool` admits a single holder, so a tab takes the `sqlite-opfs-sahpool` Web Lock when it comes to the foreground — on screen *and* holding the keyboard — and gives it back when it leaves (`sqlite3-dictionary.js`, ADR-0012). Visibility alone would not do: two tabs can be visible side by side and only one is being used. A tab without a turn answers the queries asked of it with no completions and keeps nothing, so never gate a call on readiness — just ask, and read `:dictionary/ready?` if you have to say *why* a list is empty (#312).

**User data** — PouchDB, split in two databases. Each schema in `db/documents.cljs` names its database:

- `user-db` — words, reviews, collections, examples. What follows the learner across devices: replicated to the account's CouchDB database `userdb-N` through `/db/` (ADR-0006). `adapters/learner/sync.cljs` pushes on local changes (throttled) and pulls on data-page entry; vocab conflicts resolve last-writer-wins by `:modified-at`. A device without an account is local-only and makes no network call.
- `device-db` — the device identity and migration records. Never replicated. Examples lived here until ADR-0020, and a stored task queue until ADR-0021; no start moves or sweeps them any more.

**Learner's data in memory** — what screens show of the learner's data is kept by the learner, in an atom of its own, as `:learner/memory`, a projection of user-db kept by `adapters/learner/memory.cljs` — the value, documents in and memory out — and loaded by `adapters/learner/loader.cljs` (ADR-0016, ADR-0017, ADR-0019, ADR-0020). What it guarantees is in `openspec/specs/learner-data-memory/spec.md`. The loader notes where user-db's change feed stands, reads it behind the splash, reads what was stored meanwhile and writes memory into that atom once, with `:learner/loaded?` (the application refreshes the screen when the learner reports the change, `:learner/on-change`); from then on it follows the change feed and applies each batch to memory itself, and catches up by one read (`catch-up!`) when the page is shown again. A write catches memory up with user-db's change log once PouchDB has accepted it (ADR-0019); nothing else writes memory. A repeat start takes memory from a snapshot in the Cache API (`adapters/learner/snapshot.cljs`, ADR-0018, ADR-0023) and catches up from the feed position memory keeps; what the snapshot must pass and when it is written is in the same spec.

**The learner port** — use cases reach the learner's data through one port, `ports/learner.cljs` over `adapters/learner.cljs`: memory to read, every write, the active collection, sync (`:learner/sync!`, `:learner/has-account?`, `:learner/sync-link`, `:learner/on-device-joined`) and backup. `:learner/initialized` is a promise that memory is loaded and the first sync pass is done, or a minute has passed. A write reads the document it changes from PouchDB by id, as the winner, puts its change over it (`db.pouch/write-latest!`) and catches memory up with user-db's change log. It queries no index.

`db_migrations.cljs` runs before `db.pouch` opens the databases; it once split the old single `local-db` into these two.

**Service worker** — plain JS, `resources/public/js/sw.js`; does not run ClojureScript. The backend serves it at `/js/app/sw.js` and prepends `SW_VERSION` (the cache bucket name) and `PRECACHE_URLS` (`src/backend/core.clj`, `service-worker-handler`). A new build waits for every window of the app to close (ADR-0022).

**Examples** — `application.example-backfill`, started as the `:examples/backfill` component and deciding by the rules of `use-cases.examples`, is a background process over the ports (the examples generator, the learner, the world and the browser). It is a loop: one request at a time for the first pair of entry and collection that has no example (`domain/examples.cljs`), while the tab is visible and the device is online and has an account. After each request it pauses; with nothing missing it waits until memory's words or collections change; hidden or offline, until the page is active again (`:browser/*`, shared with sync's push socket). There is no periodic look (ADR-0021). Memory and the vocabulary use case know nothing about it. What it must do is in `openspec/specs/example-backfill/spec.md`.

**Async pitfalls** — `^:async` fns await some forms without an `await`; see [docs/dev/cljs-async-pitfalls.md](../../docs/dev/cljs-async-pitfalls.md).

## Building

```bash
npx shadow-cljs watch app          # dev build + hot reload
npx shadow-cljs release app        # production build
npx shadow-cljs compile node-test && node target/node-tests.js   # client tests
```

Client test namespaces live in `test/client/` and run in Node. Browser specs are Playwright, in `test/browser/` (`npm run test:browser`).
