# 0005. Client runtime system and component graph

- Status: accepted
- Date: 2026-05-17

## Context

Client feature code depended on global runtime modules for storage, tasks, dictionary access, router navigation, and Nexus dispatch. That made tests depend on browser/runtime details and made parallel agent work risky because unrelated feature slices converged on the same global modules and page effects.

The client also has resources with different lifecycle needs. PouchDB progress storage must run blocking migrations before dependent work starts. The dictionary SQLite worker must expose a safe handle before routing, while the dictionary data itself may finish preparing asynchronously. Router startup must happen last because it triggers the first page transition and page effects.

## Decision

Use a small explicit client runtime system. `runtime/system.cljs` starts a data-driven component graph and returns `{:values :stops :plan}`. Long-lived resources stay in component values and stop functions, outside reactive app state.

Nexus receives a smaller app context as its `system` value:

```clojure
{:store app-state*
 :capabilities {:dictionary ...
                :examples ...
                :navigation ...
                :progress-store ...}}
```

`register-system->state!` extracts `@(:store system)`, and effect handlers receive app capabilities through an interceptor that reads `(:capabilities system)`.

Represent startup as a data-driven component graph. Component keys name app capabilities or concrete resources, such as `:port/dictionary`, `:port/progress-store`, `:db/sqlite`, `:db/pouch`, `:worker/task-runner`, and `:app/router`. Each component definition declares local dependency names with `:requires`; `:after` declares order-only dependencies. The lifecycle manager resolves dependency layers, starts each layer in parallel, passes accumulated local dependency values to start functions, and stops started components in reverse order on failure or shutdown.

Public use cases may accept the full capabilities map at the effect boundary, but they must destructure the required ports immediately. Private helpers, port functions, adapter functions, and domain functions receive narrow arguments only.

Clock is not a public app capability. It is injected into adapters/runtime components that stamp timestamps or schedule retries.

## Consequences

- Long-lived resources cannot be accidentally reset by UI state updates or route transitions.
- Startup order is explicit and testable; storage migrations complete before dependent ports, and router startup remains last.
- Dictionary handle readiness and dictionary data readiness are separate: page effects can safely call the port after router start, while data preparation may continue in the background.
- Feature slices can be migrated independently by depending on ports instead of global storage/task/router modules.
- Use-case tests can pass supplied capabilities without starting router or worker infrastructure unless the adapter itself is under test.
- The runtime manager becomes a small local abstraction that must be tested for missing deps, cycles, async startup, parallel layer startup, reverse stop, and failed-start cleanup.
- Passing the full capabilities map too deep would recreate a service-locator anti-pattern; reviews and tests should enforce the boundary rule that only public use cases accept the full map.
