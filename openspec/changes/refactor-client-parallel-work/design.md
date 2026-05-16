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

Alternative considered: protocols. Rejected for first pass because this is a refactor for boundaries and tests, not polymorphism complexity.

### 5. Adapter lifecycle is uniform

Adapters should expose startup that returns a public value and cleanup function. No-resource adapters return a no-op cleanup function. Runtime startup composes them and records stops.

Migrations run as part of storage adapter/runtime startup before storage capability is exposed. Slow background work remains task-queue work, not startup-blocking migration.

### 6. Refactor in dependency order

Do not rewrite every feature at once. Establish system/deps first, then port boundaries, then migrate page effects and use cases slice by slice.

Suggested implementation order:
1. Add `runtime/system.cljs` and adapt `main.cljs`/`application.cljs`.
2. Add `ports/*` capability modules and initial adapters wrapping current `dbs`, dictionary worker, tasks, navigation, telemetry, and clock.
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

- Should `:navigation` wrap only `rfe/navigate`/`replace-state`, or also route/controller startup?
- Should examples get their own `:examples` capability or stay under `:progress-store` in first pass?
- Should telemetry be console/log-only now, or reserve shape for backend event delivery later?
