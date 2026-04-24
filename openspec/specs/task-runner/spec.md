# task-runner Specification

## Purpose
The task runner spec defines how the client processes asynchronous task documents with retries, offline pause/resume, and fair selection ordering.
## Requirements
### Requirement: Task documents are persisted
The system SHALL persist asynchronous work as task documents defined in the data-model spec.

#### Scenario: Create example-fetch task
- **WHEN** a task is created for a word
- **THEN** the task document matches the task document shape in `specs/data-model/spec.md`

### Requirement: Task runner processes tasks in parallel
The system SHALL execute tasks using a configurable worker pool with fair task ordering.

#### Scenario: Worker pool handles multiple tasks
- **WHEN** multiple tasks are pending
- **THEN** the runner processes them concurrently up to the configured pool size

#### Scenario: Fair task ordering
- **WHEN** tasks are selected for execution
- **THEN** they are ordered by `run-at` and then `created-at`
- **AND** completion order MAY differ due to concurrent execution

### Requirement: Offline pause
The system SHALL pause task execution while offline and resume when online.

#### Scenario: Offline pause and resume
- **WHEN** the app goes offline
- **THEN** pending tasks are not executed until online

### Requirement: Retry with exponential backoff
The system SHALL reschedule failed tasks with exponential backoff, capped at 1 minute, until the configured retry limit is reached.

#### Scenario: Backoff on failure
- **WHEN** a task fails below the configured retry limit
- **THEN** attempts increment and run-at is set with capped backoff

#### Scenario: Retry limit reached
- **WHEN** a retryable task fails at the configured retry limit
- **THEN** the task is marked as failed with a failure reason
- **AND** the task is not rescheduled automatically

### Requirement: Task cleanup is required
The system SHALL ensure that completed tasks are removed.

#### Scenario: Completed task removal
- **WHEN** a task completes successfully
- **THEN** the task document is removed with best-effort conflict resolution (including refetching latest revisions) and is not rescheduled

### Requirement: Unknown tasks are dead-lettered
The system SHALL mark tasks with unknown types as dead-lettered.

#### Scenario: Unknown task type
- **WHEN** a task type has no registered handler
- **THEN** the task document is marked as failed with a reason and is not rescheduled

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

