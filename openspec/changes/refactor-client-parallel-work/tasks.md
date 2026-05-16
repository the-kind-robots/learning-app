## 1. Runtime System Boundary

- [ ] 1.1 Add `src/client/runtime/system.cljs` with startup result shape `{:store :deps :state :stops}`.
- [ ] 1.2 Change `main.cljs` dispatch to pass runtime system to Nexus instead of raw app-state atom.
- [ ] 1.3 Change `register-system->state!` usage to extract `@(:store system)`.
- [ ] 1.4 Change shared `:effect/save` to write to `(:store system)`.
- [ ] 1.5 Add Nexus interceptor that exposes `:deps` in effect context from `[:system :deps]`.
- [ ] 1.6 Add tiny data-driven lifecycle manager that validates dependency keys, rejects cycles, starts in topological order, awaits async starts, and stops in reverse order.
- [ ] 1.7 Ensure startup failure stops already-started components in reverse order.
- [ ] 1.8 Preserve boot order: store, progress storage, migrations, task queue/runner, dictionary handle, deps, Nexus, render binding, service worker registration, router last.
- [ ] 1.9 Make dictionary handle available before router start while allowing dictionary data readiness to complete asynchronously after router start.

## 2. Ports and Adapters

- [ ] 2.1 Add minimal `ports/progress_store.cljs` capability for migrated PouchDB-backed vocabulary, reviews, examples, tasks, and lesson persistence used by current use cases.
- [ ] 2.2 Add minimal `ports/dictionary.cljs` capability for `ready?` and completions where completions are safe before data readiness.
- [ ] 2.3 Add minimal `ports/task_queue.cljs` capability for enqueue/run-now behavior used by examples/tasks.
- [ ] 2.4 Add minimal `ports/navigation.cljs` capability for navigate and replace navigation.
- [ ] 2.5 Add minimal `ports/telemetry.cljs` and `ports/clock.cljs` capabilities wrapping existing log/time behavior.
- [ ] 2.6 Add adapters that wrap current progress storage/`dbs`, dictionary worker handle, `tasks`, `reitit.frontend.easy`, logging, and `utils` without changing behavior.

## 3. Feature Slice Migration

- [ ] 3.1 Migrate home effects and vocabulary add/count use cases to consume `deps` instead of `(dbs/dbs)`.
- [ ] 3.2 Migrate words effects and vocabulary list/update/delete use cases to consume `deps`.
- [ ] 3.3 Migrate lesson effects and lesson use cases to consume `deps` and navigation capability.
- [ ] 3.4 Migrate example-fetch enqueue/execution path behind task queue and storage capabilities where practical.
- [ ] 3.5 Keep domain namespaces pure and free of runtime deps.

## 4. Tests and Parallel-Safety Checks

- [ ] 4.1 Add use-case tests with fake deps for at least one home/vocabulary path.
- [ ] 4.2 Add use-case tests with fake deps for at least one lesson path.
- [ ] 4.3 Add focused runtime/system tests for dependency ordering, async startup, reverse stop, failed-start cleanup, deps injection, and app-state extraction.
- [ ] 4.4 Keep adapter tests explicit when they require PouchDB, SQLite worker proxy, browser navigation, or scheduler internals.
- [ ] 4.5 Add or document code-search checks proving page effects no longer call `dbs/dbs` directly for feature logic.

## 5. Verification and Closeout

- [ ] 5.1 Run `npx shadow-cljs compile node-test`.
- [ ] 5.2 Run `node target/node-tests.js`.
- [ ] 5.3 Run `npx shadow-cljs compile app`.
- [ ] 5.4 Run targeted OpenSpec validations for `refactor-client-parallel-work`, `client-runtime-dependencies`, `main-thread-runtime`, and `app-logic-testing`.
- [ ] 5.5 Update design/tasks if implementation discovers a better boundary before archive.
