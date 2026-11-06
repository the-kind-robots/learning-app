## MODIFIED Requirements

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

#### Scenario: The history is followed after runtime dependencies
- **WHEN** the app starts following the browser history
- **THEN** app store, migrated PouchDB handle, dictionary port handle, app capabilities, Nexus dispatch, and rendering are already initialized
- **AND** the entry of the first window can read capabilities safely

#### Scenario: Startup failure cleans up partial system
- **WHEN** a component fails during startup after other components already started
- **THEN** runtime startup stops already-started components in reverse startup order
- **AND** reports the startup failure

#### Scenario: Shutdown runs in reverse order
- **WHEN** runtime shutdown is requested
- **THEN** cleanup functions run in reverse startup order
- **AND** cleanup is best-effort so one cleanup failure does not prevent later cleanup attempts
