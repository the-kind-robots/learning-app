# 0005. Client runtime system and component graph

- Status: accepted
- Date: 2026-05-17

## Context

Client feature code currently depends on global runtime modules for storage, tasks, dictionary access, router navigation, and Nexus dispatch. That makes tests depend on browser/runtime details and makes parallel agent work risky because unrelated feature slices converge on the same global modules and page effects.

The client also has resources with different lifecycle needs. PouchDB progress storage must open and run blocking migrations before dependent work starts. The dictionary SQLite worker must expose a safe handle before routing, while the dictionary data itself may finish preparing asynchronously. Router startup must happen last because it triggers the first page transition and page effects.

## Decision

Use a small explicit client runtime system. Runtime resources live outside reactive app state and are owned by a startup result shaped like `{:store :deps :state :stops}`. Nexus receives this runtime system as its `system` value; `register-system->state!` extracts `@(:store system)`, and effect handlers receive app dependencies through an interceptor that reads `[:system :deps]`.

Represent startup as a data-driven component graph. Component keys name app capabilities or concrete resources, such as `:port/dictionary`, `:port/progress-store`, `:sqlite/dictionary-db`, and `:app/router`. Each component definition declares local dependency names with `:requires`, and the selected adapter implementation with `:start`. The lifecycle manager resolves dependency layers, starts each layer in parallel, passes accumulated local dependency values to start functions, and stops started components in reverse order on failure or shutdown.

Public use cases may accept the full deps map at the effect boundary, but they must destructure the required ports immediately. Private helpers, port functions, and domain functions receive narrow arguments only.

## Consequences

- Long-lived resources cannot be accidentally reset by UI state updates or route transitions.
- Startup order is explicit and testable; storage migrations complete before dependent tasks, and router startup remains last.
- Dictionary handle readiness and dictionary data readiness are separate: page effects can safely call the port after router start, while data preparation may continue in the background.
- Feature slices can be migrated independently by depending on ports instead of global storage/task/router modules.
- Use-case tests can pass fake deps without starting PouchDB, SQLite workers, Reitit, or browser APIs.
- The runtime manager becomes a small local abstraction that must be tested for missing deps, cycles, async startup, parallel layer startup, reverse stop, and failed-start cleanup.
- Passing the full deps map too deep would recreate a service-locator anti-pattern; reviews and tests should enforce the boundary rule that only public use cases accept full deps.
