## Why

Client feature work reached through global runtime modules (`dbs`, tasks, dictionary worker, router) from page effects and use cases. That coupling made parallel agent work risky because unrelated slices touched the same files and use-case tests needed concrete browser/storage setup.

This change creates explicit runtime dependencies and small client ports so future work can split by vertical slice or adapter ownership.

## What Changes

- Introduce `runtime/system`, a tiny data-driven lifecycle manager for client components.
- Pass a small app context (`:store` + `:capabilities`) as the Nexus `system` value instead of passing the raw app-state atom.
- Add a Nexus interceptor that exposes `:capabilities` to effect handlers while keeping capabilities out of reactive app state.
- Add small client ports for app capabilities: progress storage, dictionary lookup, examples, navigation, plus runtime clock injection for adapters.
- Refactor public use cases to accept `capabilities` and destructure required ports immediately.
- Keep private helpers, port functions, adapter functions, and domain functions on narrow arguments only.
- Move direct DB, worker, router, migration, and task lifecycle access out of page effects and use cases where practical.
- Preserve current user-visible behavior; this is an architecture refactor, not a UX/data-model change.

## Capabilities

### New Capabilities
- `client-runtime-dependencies`: Defines runtime system ownership, capabilities map rules, lifecycle boundaries, and client port contracts for parallel-safe work.

### Modified Capabilities
- `main-thread-runtime`: App boot and Nexus dispatch should use runtime app context + app-state extraction, not the raw app-state atom as the whole system.
- `app-logic-testing`: Use-case and effect tests should be able to run against supplied capabilities/fakes without browser, SQLite worker, or PouchDB where the tested behavior does not require those adapters.

## Impact

- Affected code: `src/client/main.cljs`, `src/client/application.cljs`, `src/client/db/**`, `src/client/tasks.cljs`, `src/client/db_migrations.cljs`, `src/client/pages/**/effects.cljs`, `src/client/use_cases/**`, `src/client/ports/**`, `src/client/adapters/**`, and related tests.
- No production data migration expected beyond existing client DB migrations continuing to run at startup.
- No new external dependency expected.
