## ADDED Requirements

### Requirement: Unique tasks are idempotent
The system SHALL support deterministic task ids for task types that represent one active unit of work per normalized task data.

#### Scenario: Ensure unique task twice
- **WHEN** the same task type and normalized data are ensured twice
- **THEN** the existing task is reused
- **AND** no second active task document is created

#### Scenario: Unique task creation conflict
- **WHEN** a unique task insert conflicts because another app instance created it first
- **THEN** the existing task is fetched and returned
- **AND** the conflict is not exposed as a user-visible failure

### Requirement: Failed tasks retain error detail
The system SHALL retain the latest failure detail for retryable task failures when error information is available.

#### Scenario: Retryable task fails with error
- **WHEN** a retryable task fails with an error message or structured error data
- **THEN** the task stores latest failure detail for debugging and UI state

## MODIFIED Requirements

### Requirement: Retry with exponential backoff
The system SHALL reschedule failed tasks with exponential backoff, capped at 1 minute, until the configured retry limit is reached.

#### Scenario: Backoff on failure
- **WHEN** a task fails below the configured retry limit
- **THEN** attempts increment and run-at is set with capped backoff

#### Scenario: Retry limit reached
- **WHEN** a retryable task fails at the configured retry limit
- **THEN** the task is marked as failed with a failure reason
- **AND** the task is not rescheduled automatically
