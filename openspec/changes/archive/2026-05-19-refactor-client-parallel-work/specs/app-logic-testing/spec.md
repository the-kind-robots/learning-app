## ADDED Requirements

### Requirement: App logic supports fast isolated tests
The system SHALL support fast tests for app logic without requiring full browser or server setup when behavior can be verified with supplied capabilities or fakes.

#### Scenario: Use case test supplies capabilities
- **WHEN** a test exercises public client use-case behavior
- **THEN** it can supply capability-shaped maps for progress storage, dictionary, examples, and navigation as needed
- **AND** the use case does not require browser navigation, SQLite worker, or task scheduler internals unless the adapter itself is under test

#### Scenario: Adapter tests remain explicit
- **WHEN** a test covers a concrete adapter such as PouchDB storage, SQLite worker proxy, navigation, clock-based timestamping, or task scheduling
- **THEN** the test sets up that adapter explicitly
- **AND** use-case tests do not inherit that setup accidentally
