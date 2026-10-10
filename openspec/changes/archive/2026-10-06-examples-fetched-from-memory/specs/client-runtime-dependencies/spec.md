## MODIFIED Requirements

### Requirement: Runtime system owns client dependencies
The client runtime MUST keep long-lived dependencies and implementation resources outside Nexus app state.

#### Scenario: Runtime startup owns component values
- **WHEN** the client app boots
- **THEN** runtime startup creates a result containing started component `:values`, cleanup `:stops`, and the compiled startup `:plan`
- **AND** long-lived resources such as workers, DB handles, the example fetcher's state, listeners, and router startup are owned by runtime components
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
- **THEN** app store, migrated PouchDB handle, dictionary port handle, app capabilities, Nexus dispatch, and rendering are already initialized
- **AND** route controller effects can read capabilities safely during the first page transition

#### Scenario: Startup failure cleans up partial system
- **WHEN** a component fails during startup after other components already started
- **THEN** runtime startup stops already-started components in reverse startup order
- **AND** reports the startup failure

#### Scenario: Shutdown runs in reverse order
- **WHEN** runtime shutdown is requested
- **THEN** cleanup functions run in reverse startup order
- **AND** cleanup is best-effort so one cleanup failure does not prevent later cleanup attempts

## ADDED Requirements

### Requirement: Background work and migrations fit runtime boundaries
Migrations and background work MUST be coordinated by runtime/adapters without leaking scheduling or schema details into feature logic.

#### Scenario: Migrations run before storage ports are ready
- **WHEN** runtime starts PouchDB-backed storage
- **THEN** required migrations complete before the PouchDB handle and dependent public storage capability are exposed as ready

#### Scenario: Background example fetching goes through capabilities
- **WHEN** the client fetches examples in the background
- **THEN** it reaches the network through the examples capability and saves through the learner capability
- **AND** it keeps its schedule in memory, with no stored task

## REMOVED Requirements

### Requirement: Tasks and migrations fit runtime boundaries

**Reason**: There are no stored tasks; the same boundary is stated for background work.
**Migration**: Requirement: Background work and migrations fit runtime boundaries.
