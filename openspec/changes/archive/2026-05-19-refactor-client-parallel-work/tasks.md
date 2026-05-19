## 1. Runtime System Boundary

- [x] 1.1 Add `src/client/runtime/system.cljs` with startup result shape `{:values :stops :plan}`.
- [x] 1.2 Change `main.cljs` dispatch to pass a runtime app context to Nexus instead of raw app-state atom.
- [x] 1.3 Change `register-system->state!` usage to extract `@(:store system)`.
- [x] 1.4 Change shared `:effect/save` to write to `(:store system)`.
- [x] 1.5 Add Nexus interceptor that exposes `:capabilities` in effect context from `(:capabilities system)`.
- [x] 1.6 Add tiny data-driven lifecycle manager that resolves dependency layers from `:requires` and `:after`, compiles layers with `[key component-definition]` entries, starts layers with accumulated local dependency args, awaits async starts, and stops in reverse order.
- [x] 1.7 Ensure startup failure stops already-started components in reverse order.
- [x] 1.8 Preserve boot order: store, migrated PouchDB handle, task runner, dictionary SQLite handle, public ports, capabilities, render binding, PWA init, service worker/listeners, router last.
- [x] 1.9 Make dictionary handle available before router start while allowing dictionary data readiness to complete asynchronously after router start.

## 2. Ports and Adapters

- [x] 2.1 Add minimal `ports/progress_store.cljs` capability for PouchDB-backed vocabulary, reviews, and lesson persistence used by current use cases.
- [x] 2.2 Add minimal `ports/dictionary.cljs` capability for `:dictionary/ready?` and `:dictionary/completions`.
- [x] 2.3 Add task-runner lifecycle wrapper so scheduling starts through the component graph instead of page/use-case globals.
- [x] 2.4 Add minimal `ports/navigation.cljs` capability for navigate and replace navigation.
- [x] 2.5 Add minimal `ports/clock.cljs` capability for adapter/runtime time behavior.
- [x] 2.6 Add component definitions in `main.cljs` that bind local `:requires` names to chosen implementations for PouchDB, SQLite dictionary worker, public ports, task runner, PWA init, and router startup.
- [x] 2.7 Remove stale client namespaces replaced by ports/adapters.

## 3. Feature Slice Migration

- [x] 3.1 Migrate home effects and vocabulary add/count use cases to consume `capabilities` instead of `(dbs/dbs)`.
- [x] 3.2 Migrate words effects and vocabulary list/update/delete use cases to consume `capabilities`.
- [x] 3.3 Migrate lesson effects and lesson use cases to consume `capabilities` and navigation capability.
- [x] 3.4 Migrate example-fetch enqueue/execution path behind examples adapter and task-runner lifecycle where practical.
- [x] 3.5 Keep domain namespaces pure and free of runtime deps.

## 4. Tests and Parallel-Safety Checks

- [x] 4.1 Update vocabulary use-case tests to call public use cases through capability-shaped deps.
- [x] 4.2 Update lesson use-case tests to call public use cases through capability-shaped deps.
- [x] 4.3 Add focused runtime/system tests for dependency ordering, async startup, reverse stop, failed-start cleanup, and local dependency args.
- [x] 4.4 Keep adapter tests explicit when they require PouchDB, SQLite worker proxy, browser navigation, clock-based timestamping, or scheduler internals.
- [x] 4.5 Add code-search verification that page effects no longer call `dbs/dbs` directly for feature logic.

## 5. Verification and Closeout

- [x] 5.1 Run `clj-kondo --lint src/client test/client`.
- [x] 5.2 Run `npx shadow-cljs compile node-test`.
- [x] 5.3 Run `node target/node-tests.js`.
- [x] 5.4 Run `npx shadow-cljs compile app`.
- [x] 5.5 Run targeted OpenSpec validations for `refactor-client-parallel-work`, `dictionary-storage`, `client-runtime-dependencies`, `main-thread-runtime`, and `app-logic-testing`.
- [x] 5.6 Update design/specs/tasks/ADR where implementation discovered a better boundary.
