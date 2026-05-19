# app-logic-testing Specification

## Purpose
TBD - created by archiving change add-app-logic-tests. Update Purpose after archive.
## Requirements
### Requirement: Word operations are covered by unit tests
The system SHALL provide unit tests for adding, updating, filtering, and deleting words using a local PouchDB instance, without external network services.

#### Scenario: Add, update, filter, delete words offline
- **WHEN** word operations run in tests with local DB fixtures
- **THEN** they complete deterministically without external services

### Requirement: Lesson flow is covered by unit tests
The system SHALL provide unit tests for lesson trial generation, answer checking, and lesson completion using local DB fixtures, without external services.

#### Scenario: Lesson flow runs deterministically
- **WHEN** lesson logic is executed in tests with local DB fixtures
- **THEN** trial selection, answer checking, and completion behave predictably

### Requirement: Testability architecture is evaluated
The system SHALL isolate pure app logic from side effects by extracting domain logic into `src/client/domain` and documenting the IO boundary decisions.

#### Scenario: IO boundaries are explicit
- **WHEN** app logic modules are reviewed
- **THEN** IO boundaries and implemented refactors are documented

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

