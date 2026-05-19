## Context

The client runs on the main thread with Replicant rendering and Nexus actions/effects. Before this change, page effects and use cases reached into global runtime modules such as `dbs`, task scheduling, dictionary worker setup, and router navigation.

This made unrelated feature work converge on the same files and made use-case tests depend on browser/storage setup.

## Goals / Non-Goals

**Goals:**
- Keep long-lived runtime resources outside reactive app state.
- Make effect handlers receive app capabilities uniformly through Nexus context.
- Make public use cases depend on small capabilities, not DB/worker/router globals.
- Keep adapter/runtime concerns behind ports so future work can split by feature or adapter.
- Keep the lifecycle manager small and understandable.

**Non-Goals:**
- No UI redesign.
- No storage model replacement.
- No behavior changes for dictionary, lessons, vocabulary, examples, or tasks.
- No external DI/system framework.
- No telemetry implementation in this change.

## Decisions

### 1. Runtime manager owns component values; Nexus receives app context

`runtime/system.cljs` starts component definitions and returns runtime-owned data:

```clojure
{:values {component-key started-value}
 :stops  [stop-fn ...]
 :plan   [[[component-key component-definition] ...] ...]}
```

Reactive UI state remains a separate `atom`. The Nexus `system` value used by effect handlers is a small app context built by the render component:

```clojure
{:store        app-state*
 :capabilities {:dictionary ...
                :examples ...
                :navigation ...
                :progress-store ...}}
```

`register-system->state!` extracts `@(:store system)`. `:effect/save` writes to `(:store system)`. Capabilities are not stored in the reactive app-state atom.

### 2. Effects receive capabilities through interceptor/context

A Nexus interceptor adds `:capabilities` to effect context from `(:capabilities system)`. Effects call use cases like `(vocabulary/count capabilities)` and never construct DB, worker, task, or router handles themselves.

### 3. Public use cases accept full capabilities; internals stay narrow

Public use cases are the boundary between effects and app logic. They may accept the full `capabilities` map, but destructure required capabilities immediately. Private helpers receive exact data or exact port handles. Domain functions receive data only.

This avoids pushing a service locator deep into app logic.

### 4. Ports are small maps of namespaced functions

Ports are plain maps of functions, not protocols. Current app-facing capabilities are:

- `:dictionary` with `:dictionary/ready?` and `:dictionary/completions`
- `:progress-store` with `:progress-store/*` word/review/lesson persistence functions
- `:examples` with `:examples/list`, `:examples/request!`, etc.
- `:navigation` with `:navigation/navigate` and `:navigation/replace`

`clock` is not an app capability. It is injected into adapters/runtime components that stamp time or schedule retries.

### 5. Adapters and resources are bound by component definitions

Component keys describe roles in this app. Adapter implementation choice lives in the component definition, not in feature code.

Actual graph shape:

```clojure
{:app/store {}
 :worker/service-worker {}
 :document/listeners {}
 :db/sqlite {}
 :db/pouch {}
 :port/clock {}
 :worker/task-runner {:requires {:db :db/pouch
                                 :clock :port/clock}}
 :port/dictionary {:requires {:db :db/sqlite}}
 :port/progress-store {:requires {:db :db/pouch
                                  :clock :port/clock}}
 :port/examples {:requires {:db :db/pouch
                            :clock :port/clock}}
 :port/navigation {}
 :app/capabilities {:requires {:dictionary :port/dictionary
                               :examples :port/examples
                               :navigation :port/navigation
                               :progress-store :port/progress-store}}
 :app/render {:requires {:capabilities :app/capabilities
                         :store :app/store}}
 :pwa/init {:requires {:render :app/render}}
 :app/router {:requires {:render :app/render}
              :after [:worker/service-worker
                      :document/listeners]}}
```

The task runner is a lifecycle component, not a public use-case dependency. Feature logic requests example generation through the examples capability (`:examples/request!`), and the adapter enqueues a task using the scheduler internals.

### 6. Lifecycle manager is small and data-driven

`runtime/system.cljs` implements only what the client needs:

- validate missing dependency keys from `:requires` and `:after`
- reject dependency cycles
- resolve dependency layers
- compile layers to `[key component-definition]` entries using `find`
- start each layer concurrently with `Promise.allSettled`
- pass start functions a local dependency map based on `:requires`
- await promise-returning starts
- collect optional stop functions from component `:stop`
- stop already-started components in reverse order if startup fails
- stop all components in reverse order on shutdown, best-effort

This keeps the useful Clojure Component/Integrant idea—dependencies as data—without adding a backend system framework to the browser bundle.

### 7. Startup ordering

Router startup remains last because `rfe/start!` applies the current route and triggers the first page load effect.

Required ordering:

1. create app store
2. run PouchDB migrations before `:db/pouch` is exposed
3. start clock and DB/resource components
4. start task runner after PouchDB and clock
5. expose dictionary/progress/examples/navigation ports
6. build `:app/capabilities`
7. install Nexus dispatch and Replicant render binding
8. run PWA init effect
9. start router after service-worker/listener startup has completed

Dictionary has handle readiness and data readiness. The dictionary port exists before router start. If SQLite data is still preparing, `:dictionary/ready?` returns false and completions safely return no results.

## Risks / Trade-offs

- Startup order can regress boot behavior → keep runtime tests and graph assertions.
- Ports can hide simple code if they grow too much → keep them as minimal function maps.
- Passing full capabilities too deep can recreate coupling → keep full map only at public use-case boundary.
- Adapter tests may still need concrete PouchDB or task scheduler setup → keep those explicit and separate from use-case tests.

## Migration Plan

1. Create runtime lifecycle manager and app capabilities boundary.
2. Add ports/adapters for dictionary, progress store, examples, navigation, clock, PouchDB, and SQLite worker.
3. Move page effects to call use cases with `capabilities`.
4. Move use cases off raw DB/router/task globals.
5. Keep domain namespaces pure.
6. Remove stale namespaces once the new ports/adapters replace them.
7. Validate with lint, node tests, app compile, and OpenSpec validation.

## Open Questions

- None for this change. Telemetry stays future work; add a telemetry port only when code actually emits app telemetry.
