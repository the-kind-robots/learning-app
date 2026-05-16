## MODIFIED Requirements

### Requirement: App logic supports fast isolated tests
The system SHALL support fast tests for app logic without requiring full browser or server setup when behavior can be verified with fakes.

#### Scenario: Use case test uses fake deps
- **WHEN** a test exercises public client use-case behavior
- **THEN** it can supply fake deps/ports for progress storage, dictionary, task queue, telemetry, navigation, and clock as needed
- **AND** the test does not require real PouchDB, SQLite worker, browser navigation, or task scheduler internals unless the adapter itself is under test

#### Scenario: Adapter tests remain explicit
- **WHEN** a test covers a concrete adapter such as PouchDB storage, SQLite worker proxy, navigation, or task scheduling
- **THEN** the test sets up that adapter explicitly
- **AND** use-case tests do not inherit that setup accidentally
