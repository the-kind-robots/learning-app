## Context

The client now runs on the main thread with Replicant rendering and Nexus actions/effects. Page effects still reach directly into global runtime modules (`dbs/dbs`, `dbs/init!`, `tasks/start!`, `tasks/create-task!`, `rfe/replace-state`) and use cases still accept a low-level `dbs` map.

This works functionally, but it keeps feature slices coupled to storage, worker, router, and task implementation details. It also makes parallel agent work harder because independent changes converge on page effects and global modules.

## Goals / Non-Goals

**Goals:**
- Make runtime resources explicit and non-reactive.
- Make effect handlers receive deps uniformly through Nexus runtime system/context.
- Make public use cases depend on small capabilities, not concrete DB/worker/router globals.
- Allow fake deps in use-case tests.
- Create file/module boundaries that let agents work in parallel with lower conflict risk.

**Non-Goals:**
- No UI redesign.
- No storage model replacement.
- No behavior changes for dictionary, lessons, vocabulary, examples, or tasks.
- No custom dependency injection framework.
- No broad cleanup of every old helper if it does not block the dependency boundary.

## Decisions

### 1. Runtime system is the Nexus `system`

Use a runtime system value as Nexus `system`:

```clojure
{:store app-state*
 :deps  {:progress-store ...
         :dictionary ...
         :task-queue ...
         :telemetry ...
         :navigation ...
         :clock ...}
 :state {:... runtime internals ...}
 :stops [stop-fn ...]}
```

`register-system->state!` should extract `@(:store system)`. `:effect/save` should write to `(:store system)`, not assume system itself is the app-state atom.

Alternative considered: keep `store` as system and put deps in app state. Rejected because UI-state reset could accidentally destroy or overwrite runtime deps.

### 2. Effects get deps through interceptor/context

Add a Nexus interceptor that injects `:deps` into effect context from `[:system :deps]`. Effects can call use cases as `(vocabulary/count deps)` rather than `(vocabulary/count (dbs/dbs))`.

Alternative considered: each effect manually reads `(:deps system)`. Acceptable but noisier; interceptor makes the intended boundary visible.

### 3. Public use cases may accept full deps; internals stay narrow

Public use cases are the boundary between effects and app logic. They may accept the full deps map, but must destructure required capabilities immediately. Private helpers receive exact data or exact port handles. Domain functions remain pure data functions.

This avoids turning `deps` into a deep service locator while keeping effect code ergonomic.

### 4. Ports are maps/functions, not protocols first

Start with simple maps of functions for ports (`:progress-store`, `:dictionary`, `:task-queue`, `:navigation`, `:telemetry`, `:clock`). Prefer minimal shape over protocol machinery until the contracts stabilize.

Component keys describe the role in this app (`:port/dictionary`, `:sqlite/dictionary-db`, `:app/router`). Adapter implementation choice lives in the component definition, not in feature code.

Alternative considered: protocols. Rejected for first pass because this is a refactor for boundaries and tests, not polymorphism complexity.

### 5. Adapter lifecycle is uniform

Adapters should expose startup that returns a public value and cleanup function. No-resource adapters return a no-op cleanup function. Runtime startup composes them and records stops.

Migrations run as part of storage adapter/runtime startup before storage capability is exposed. Slow background work remains task-queue work, not startup-blocking migration.

Router startup is a lifecycle component too, but it must start last because `rfe/start!` applies the current route controllers and triggers the first page load effect. By that moment the app must already have a stable deps map and safe port handles.

### 6. Lifecycle manager is small and data-driven

Clojure community prior art points to the same core model:

- Component: explicit lifecycle, dependency injection, start in dependency order, stop in reverse order.
- Integrant: data-driven config, references between keys, `init` / `halt!`, dependency order, reverse shutdown, idempotent halt.
- Mount: convenient global `defstate`, but order is inferred from namespace requires and state is var/global oriented.
- Clip and donut.system: data-driven component definitions with references and explicit start/stop signals.

For this browser client, we should not adopt a full backend-style component framework yet. We mostly need component definitions whose dependency wiring is explicit:

```clojure
{:app/store
 {:start (fn [_] (lifecycle/started app-state*))}

 :port/progress-store
 {:start progress-pouch/start!} ; opens PouchDB handles and runs blocking migrations

 :port/task-queue
 {:requires {:progress-store :port/progress-store
             :clock          :port/clock
             :telemetry      :port/telemetry}
  :start task-queue-pouch/start!}

 :sqlite/dictionary-db
 {:requires {:telemetry :port/telemetry}
  :start sqlite-worker/start-dictionary-db!}

 :port/dictionary
 {:requires {:db :sqlite/dictionary-db}
  :start dictionary-sql/start!}

 :app/deps
 {:requires {:progress-store :port/progress-store
             :dictionary     :port/dictionary
             :task-queue     :port/task-queue
             :navigation     :port/navigation
             :clock          :port/clock
             :telemetry      :port/telemetry}
  :start build-deps}

 :app/nexus
 {:requires {:store :app/store
             :deps  :app/deps}
  :start start-nexus!}

 :app/render
 {:requires {:store :app/store
             :nexus :app/nexus}
  :start start-render!}

 :app/service-worker
 {:requires {:nexus :app/nexus}
  :start register-service-worker!}

 :app/router
 {:requires {:nexus :app/nexus
             :render :app/render
             :deps :app/deps}
  :start start-router!}}
```

Runtime manager responsibilities:

- validate missing dependency keys from `:requires` values
- reject dependency cycles
- resolve dependency layers from component keys
- compile layers to `[key component-definition]` entries using `find`
- start components in topological layer order
- await Promise-returning starts
- stop already-started components in reverse order if startup fails
- stop all components in reverse order on normal shutdown
- make stop functions idempotent / best-effort
- pass each start function a local dependency map shaped by `:requires` keys
- return both component instances and the public runtime system

Boot order semantics:

1. create app store
2. start `:port/progress-store` (open progress storage and run blocking migrations)
3. start task queue / task runner after migrated storage exists
4. start dictionary SQLite handle and dictionary port handle
5. build deps map
7. install Nexus dispatch, system->state, and deps interceptor
8. install Replicant render/watch binding
9. register service worker if available
10. start frontend router last

Dictionary has two readiness phases. The dictionary port handle must exist before the router starts so page effects can safely call `dictionary/ready?` and `dictionary/completions`. The underlying SQLite component may become ready later after manifest fetch, download/import into OPFS, and SQLite open. Before data readiness, completions should return an empty result (or otherwise no-op safely), not block page startup and not expose a nil dependency.

Adapter functions do not hard-code global graph keys. Component definitions bind local names to component keys through `:requires`, e.g. `{:requires {:db :sqlite/dictionary-db}}`. Start functions receive only those local names, e.g. `(fn [{:keys [db]}] ...)`. This keeps the system wiring visible in `runtime/system.cljs` instead of hidden inside adapters.

This is Integrant/Clip-inspired but local and tiny. It keeps the browser bundle and mental model small while preserving the important Clojure lifecycle lesson: declare dependencies as data, then let the manager order startup/shutdown.

Alternative considered: add Integrant/Clip as a dependency. Rejected for first pass because current client only needs ordering and async resource cleanup, not profiles, namespace loading, custom signals, or EDN configuration.

Alternative considered: use Mount-style globals. Rejected because global state conflicts with the goal of parallel-safe tests and explicit fake deps.

### 7. Refactor in dependency order

Do not rewrite every feature at once. Establish system/deps first, then port boundaries, then migrate page effects and use cases slice by slice.

Suggested implementation order:
1. Add `runtime/system.cljs` and adapt `main.cljs`/`application.cljs`.
2. Add `ports/*` capability modules and component definitions that select adapters for progress storage/`dbs`, dictionary SQLite handle, dictionary port, task queue/runner, navigation, telemetry, and clock.
3. Migrate home effects/use cases to deps.
4. Migrate words/vocabulary effects/use cases to deps.
5. Migrate lesson/effects/use cases to deps.
6. Migrate task enqueue and example fetch path behind task queue / example ports.
7. Tighten tests and code-search assertions.

## Risks / Trade-offs

- Runtime startup order can regress boot behavior → keep startup order explicit and add focused runtime tests.
- Too many abstractions can hide simple code → keep ports small maps of functions; add only capabilities used now.
- Passing full deps too deep can recreate coupling → enforce layer rule in tests/review: public use case accepts deps, private helpers narrow.
- Page migration can create merge conflicts → migrate one vertical slice per commit/task.
- Existing integration tests may still need concrete PouchDB → keep adapter tests separate from use-case fake-deps tests.

## Migration Plan

1. Create runtime/deps boundary while preserving existing behavior.
2. Add adapters that wrap existing implementation, not replace it.
3. Move feature slices one at a time.
4. Keep current node-test gate passing after each slice.
5. Stop once page effects no longer call `dbs/dbs` directly for feature logic and use-case tests can use fake deps.

Rollback: revert the refactor branch/PR before merge. No production data changes expected.

## Open Questions

- Should `:navigation` wrap only page navigation calls while router startup stays lifecycle-only? Current plan says yes.
- Should examples get their own `:examples` capability or stay under `:progress-store` in first pass?
- Should telemetry be console/log-only now, or reserve shape for backend event delivery later?
