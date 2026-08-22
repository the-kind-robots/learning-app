# client-runtime-dependencies Specification

## Purpose
TBD - created by archiving change refactor-client-parallel-work. Update Purpose after archive.
## Requirements
### Requirement: Runtime system owns client dependencies
The client runtime MUST keep long-lived dependencies and implementation resources outside Nexus app state.

#### Scenario: Runtime startup owns component values
- **WHEN** the client app boots
- **THEN** runtime startup creates a result containing started component `:values`, cleanup `:stops`, and the compiled startup `:plan`
- **AND** long-lived resources such as workers, DB handles, task-runner state, listeners, and router startup are owned by runtime components
- **AND** Nexus app state contains only reactive UI/domain facts

#### Scenario: Nexus receives app context
- **WHEN** Nexus dispatch runs client actions and effects
- **THEN** its system value contains the app `:store` and public `:capabilities`
- **AND** `:capabilities` contains app-facing ports rather than raw mutable resources
- **AND** capabilities are not stored under the reactive app-state atom

#### Scenario: App state reset cannot destroy dependencies
- **WHEN** a Nexus action or effect replaces page state
- **THEN** runtime dependencies, workers, DB handles, timers, and cleanup functions remain owned by runtime components
- **AND** they are not overwritten by UI state changes

### Requirement: Components expose uniform lifecycle
Runtime components that manage external resources MUST expose uniform lifecycle boundaries to runtime startup.

#### Scenario: Component with resource returns live value
- **WHEN** runtime starts a component that owns a worker, DB connection, timer, listener, router, or subscription
- **THEN** the component `:start` returns the public value needed by dependents
- **AND** implementation resources remain private to the adapter/runtime layer

#### Scenario: Component with cleanup declares stop
- **WHEN** a component needs cleanup
- **THEN** its definition declares `:stop`
- **AND** runtime startup records a stop function using the started value

#### Scenario: Component without cleanup uses same shape
- **WHEN** runtime starts a fake or pure component
- **THEN** it returns the same public value shape
- **AND** it may omit `:stop`

### Requirement: Runtime manager resolves dependency order
The client runtime MUST start dependencies in declared order and stop them in reverse order.

#### Scenario: Dependencies start before dependents
- **WHEN** runtime startup receives component definitions with `:requires` bindings and `:after` order-only dependencies
- **THEN** it validates referenced dependency keys
- **AND** rejects dependency cycles
- **AND** resolves dependency layers from component keys
- **AND** starts each component only after its declared dependencies are ready
- **AND** awaits asynchronous component startup before dependent components start

#### Scenario: Start functions receive local dependency names
- **WHEN** a component definition declares `:requires {:db :db/sqlite}`
- **THEN** its start function receives a map containing `{:db <started-sqlite-value>}`
- **AND** the adapter start function does not hard-code the global `:db/sqlite` key

#### Scenario: Router starts after runtime dependencies
- **WHEN** the frontend router starts
- **THEN** app store, migrated PouchDB handle, task runner, dictionary port handle, app capabilities, Nexus dispatch, and rendering are already initialized
- **AND** route controller effects can read capabilities safely during the first page transition

#### Scenario: Startup failure cleans up partial system
- **WHEN** a component fails during startup after other components already started
- **THEN** runtime startup stops already-started components in reverse startup order
- **AND** reports the startup failure

#### Scenario: Shutdown runs in reverse order
- **WHEN** runtime shutdown is requested
- **THEN** cleanup functions run in reverse startup order
- **AND** cleanup is best-effort so one cleanup failure does not prevent later cleanup attempts

### Requirement: Dictionary handle is ready before dictionary data
The dictionary port MUST expose a stable handle before the dictionary data is fully ready,
and that handle MUST be callable at any point after it exists. A completion request made
before the data is there resolves with no completions, so no caller SHALL gate the request
on readiness. The port SHALL expose `:dictionary/ready?` as a reading rather than a gate,
because an empty answer alone cannot say whether the prefix has no completions or this
context has no dictionary.

#### Scenario: Autocomplete before dictionary data is ready
- **WHEN** the router has started and the dictionary worker is still fetching, importing, or opening dictionary data
- **THEN** the dictionary capability exists in capabilities
- **AND** `:dictionary/completions` accepts the call rather than refusing it
- **AND** it resolves with no completions, or rejects if this context failed to open the dictionary
- **AND** `:dictionary/ready?` reports false meanwhile
- **AND** page startup is not blocked by dictionary data readiness

#### Scenario: Autocomplete after dictionary data is ready
- **WHEN** the dictionary worker has opened the current SQLite dictionary
- **THEN** `:dictionary/completions` queries the dictionary worker normally
- **AND** `:dictionary/ready?` reports true

### Requirement: Public dependencies are small capabilities
The capabilities map MUST expose small app capabilities instead of implementation-specific globals.

#### Scenario: Use case receives capabilities
- **WHEN** a public use-case function is called from an effect handler
- **THEN** it may receive the full capabilities map
- **AND** it destructures required capabilities immediately
- **AND** private helpers receive narrow data or exact port handles, not the whole capabilities map

#### Scenario: Domain functions stay pure
- **WHEN** domain functions are called
- **THEN** they receive data values only
- **AND** they do not depend on runtime capabilities, ports, workers, DB handles, or browser APIs

### Requirement: Tasks and migrations fit runtime boundaries
Migrations and background tasks MUST be coordinated by runtime/adapters without leaking scheduling or schema details into feature logic.

#### Scenario: Migrations run before storage ports are ready
- **WHEN** runtime starts PouchDB-backed storage
- **THEN** required migrations complete before the PouchDB handle and dependent public storage capability are exposed as ready

#### Scenario: Feature logic requests background work through a capability
- **WHEN** feature logic needs background example generation
- **THEN** it requests that work through the examples capability
- **AND** it does not call task scheduler internals directly

