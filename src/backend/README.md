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
clj -M:dev -m core          # dev REPL with nREPL on port 7888
clj -M:test                 # run backend tests
```

See [readme.md](../../readme.md) for full dev setup (nginx, TLS, DB init).

## Database

Server-side SQLite file: `app.db`, built and kept current by the migrations in
`resources/migrations/`, which `start-server!` applies before serving. The
migration list in `migrations.clj` is the schema's single source of truth: add a
new file for every change, never edit one that has shipped.
Custom SQL functions registered at startup live in `sqlite/application_defined_functions.clj`.
Queries built with HoneySQL; executed via next.jdbc.

## API conventions

Routes defined in `core.clj` using reitit. Interceptors handle JSON coercion and keyword parameter normalization. Malli schemas validate request/response shapes.
