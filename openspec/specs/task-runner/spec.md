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
The system SHALL reschedule failed tasks with exponential backoff, capped at 1 minute.

#### Scenario: Backoff on failure
- **WHEN** a task fails
- **THEN** attempts increment and run-at is set with capped backoff

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

