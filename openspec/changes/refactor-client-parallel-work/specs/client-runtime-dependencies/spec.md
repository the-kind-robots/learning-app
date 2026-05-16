## ADDED Requirements

### Requirement: Runtime system owns client dependencies
The client runtime MUST keep long-lived dependencies and implementation resources outside Nexus app state.

#### Scenario: Runtime system contains deps and lifecycle state
- **WHEN** the client app boots
- **THEN** runtime startup creates a system value containing `:store`, `:deps`, runtime-only `:state`, and cleanup `:stops`
- **AND** `:deps` contains public app capabilities rather than raw mutable resources
- **AND** Nexus app state contains only reactive UI/domain facts

#### Scenario: App state reset cannot destroy dependencies
- **WHEN** a Nexus action or effect replaces page state
- **THEN** runtime dependencies, workers, DB handles, timers, and cleanup functions remain owned by runtime system
- **AND** they are not stored under the reactive app-state atom

### Requirement: Adapters expose uniform lifecycle
Adapters that manage external resources MUST expose uniform lifecycle boundaries to runtime startup.

#### Scenario: Adapter with resource returns live handle
- **WHEN** runtime starts an adapter that owns a worker, DB connection, timer, or subscription
- **THEN** the adapter returns a ready public value and a cleanup function
- **AND** implementation resources remain private to the adapter/runtime layer

#### Scenario: Adapter without resource still uses same shape
- **WHEN** runtime starts a fake or pure adapter
- **THEN** it returns the same public value shape
- **AND** its cleanup function is a no-op

### Requirement: Runtime manager resolves dependency order
The client runtime MUST start dependencies in declared order and stop them in reverse order.

#### Scenario: Dependencies start before dependents
- **WHEN** runtime startup receives component definitions with dependency keys
- **THEN** it validates referenced dependency keys
- **AND** rejects dependency cycles
- **AND** starts each component only after its declared dependencies are ready
- **AND** awaits asynchronous component startup before dependent components start

#### Scenario: Router starts after runtime dependencies
- **WHEN** the frontend router starts
- **THEN** app store, migrated progress storage, task queue, dictionary handle, deps map, Nexus dispatch, and rendering are already initialized
- **AND** route controller effects can read deps safely during the first page transition

#### Scenario: Startup failure cleans up partial system
- **WHEN** a component fails during startup after other components already started
- **THEN** runtime startup stops already-started components in reverse startup order
- **AND** reports the startup failure

#### Scenario: Shutdown runs in reverse order
- **WHEN** runtime shutdown is requested
- **THEN** cleanup functions run in reverse startup order
- **AND** cleanup is best-effort/idempotent so one cleanup failure does not prevent later cleanup attempts

### Requirement: Dictionary handle is ready before dictionary data
The dictionary port MUST expose a stable handle before the dictionary data is fully ready.

#### Scenario: Autocomplete before dictionary data is ready
- **WHEN** the router has started and the dictionary worker is still fetching, importing, or opening dictionary data
- **THEN** the dictionary capability exists in deps
- **AND** `ready?` reports false
- **AND** completions return an empty result or otherwise no-op safely
- **AND** page startup is not blocked by dictionary data readiness

#### Scenario: Autocomplete after dictionary data is ready
- **WHEN** the dictionary worker has opened the current SQLite dictionary
- **THEN** `ready?` reports true
- **AND** completions query the dictionary worker normally

### Requirement: Public dependencies are small capabilities
The deps map MUST expose small app capabilities instead of implementation-specific globals.

#### Scenario: Use case receives dependencies
- **WHEN** a public use-case function is called from an effect handler
- **THEN** it may receive the full deps map
- **AND** it destructures required capabilities immediately
- **AND** private helpers receive narrow data or exact port handles, not the whole deps map

#### Scenario: Domain functions stay pure
- **WHEN** domain functions are called
- **THEN** they receive data values only
- **AND** they do not depend on runtime deps, ports, workers, DB handles, or browser APIs

### Requirement: Tasks and migrations fit runtime boundaries
Migrations and background tasks MUST be coordinated by runtime/adapters without leaking scheduling or schema details into feature logic.

#### Scenario: Migrations run before storage ports are ready
- **WHEN** runtime starts storage-backed adapters
- **THEN** required migrations complete before the corresponding public storage capability is exposed as ready

#### Scenario: Use cases enqueue tasks through a task queue capability
- **WHEN** feature logic needs background work
- **THEN** it requests that work through a task queue capability
- **AND** it does not call task scheduler internals directly
