## Why

Client feature work still reaches through global runtime modules (`dbs`, `tasks`, dictionary worker, router) from page effects and use cases. That coupling makes parallel agent work risky because unrelated slices touch the same files and use-case tests need concrete browser/storage setup.

This change creates explicit runtime dependencies and small client ports so future work can be split by vertical slice or adapter ownership.

## What Changes

- Introduce a runtime dependency model: `runtime/system` owns long-lived resources, lifecycle, deps, and cleanup.
- Pass `runtime/system` as the Nexus `system` argument instead of passing the raw app-state atom.
- Add a Nexus interceptor that exposes `:deps` to effect handlers while keeping deps out of reactive app state.
- Add small client ports for public capabilities such as progress storage, dictionary lookup, task queue, telemetry, navigation, and clock.
- Refactor public use cases to accept `deps` and destructure required ports immediately.
- Keep private helpers, port functions, and domain functions on narrow arguments only.
- Move direct `dbs/dbs`, worker, router, migration, and task lifecycle access out of page effects where practical.
- Preserve current user-visible behavior; this is an architecture refactor, not a UX/data-model change.

## Capabilities

### New Capabilities
- `client-runtime-dependencies`: Defines runtime system ownership, deps map rules, lifecycle boundaries, and client port contracts for parallel-safe work.

### Modified Capabilities
- `main-thread-runtime`: App boot and Nexus dispatch should use runtime system + app-state extraction, not raw app-state atom as the whole system.
- `app-logic-testing`: Use-case and effect tests should be able to run against fake deps/ports without browser, SQLite worker, or PouchDB where the tested behavior does not require those adapters.

## Impact

- Affected code: `src/client/main.cljs`, `src/client/application.cljs`, `src/client/dbs.cljs`, `src/client/tasks.cljs`, `src/client/db_migrations.cljs`, `src/client/pages/**/effects.cljs`, `src/client/use_cases/**`, `src/client/repo/**`, and related tests.
- New structure expected: `src/client/runtime/**`, `src/client/ports/**`, and possibly `src/client/adapters/**`.
- No production data migration expected.
- No new external dependency expected.
