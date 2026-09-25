# Backend

Clojure HTTP server. Serves the SPA, provides API endpoints, and manages the server-side SQLite database.

## Stack

| Concern             | Library              |
|---------------------|----------------------|
| HTTP server         | http-kit             |
| Routing             | reitit               |
| DB access           | next.jdbc + HoneySQL |
| DB driver           | sqlite-jdbc (SQLite) |
| Schema / validation | malli                |
| JSON                | cheshire             |
| Logging             | telemere             |
| Passwords           | buddy-hashers        |

## Source layout

```
src/backend/
├── core.clj                          # Entry point — starts http-kit, wires routes
├── examples.clj                      # Fixture/example data helpers
├── examples/
│   ├── cache.clj                     # Generated examples, keyed by their request
│   ├── dictionary.clj                # Example dictionary entries
│   └── provider.clj                  # Example data provider
├── reitit/http/interceptors/
│   └── keyword_parameters.cljc       # Request parameter coercion interceptor
└── sqlite/
    └── application_defined_functions.clj  # Custom SQLite scalar/aggregate functions
```

`src/shared/` adds `.cljc` utilities visible to both backend and client.
`lib/db/` (local dep) provides DB macro helpers used in queries.

## Running

```bash
clj -M:dev -m core          # start the server with the dev alias
clojure -M:test             # run backend tests
```

`-M:test` runs every `*-test` namespace under `test/backend/` through
cognitect test-runner and exits non-zero on any failure; CI's Backend Tests
job runs the same command. A single namespace: `clojure -M:test -n
backend.push-test`. Tests open temp SQLite files and stub CouchDB, so no
database or running stand is needed.

Neither starts an nREPL. The one on the stand is shadow-cljs's, on the port
`shadow-cljs.edn` pins.

Bringing the stand up, and the nginx vhost in front of it, are in
[docs/dev/development-setup.md](../../docs/dev/development-setup.md) and
[infra/README.md](../../infra/README.md). The database needs no step of its
own: the server migrates it on boot and creates it when it is not there.

## Database

Server-side SQLite file: `app.db`, built and kept current by the migrations in
`resources/migrations/`, which `start-server!` applies before serving. The
migration list in `migrations.clj` is the schema's single source of truth: add a
new file for every change, never edit one that has shipped.
Custom SQL functions registered at startup live in `sqlite/application_defined_functions.clj`.
Queries built with HoneySQL; executed via next.jdbc.

## API conventions

Routes defined in `core.clj` using reitit. Interceptors handle JSON coercion and keyword parameter normalization. Route parameters are checked in the handler itself. Malli describes the shapes at the edges — the example the provider is asked to return, and the question the example cache is keyed by.
