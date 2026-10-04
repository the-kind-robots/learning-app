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

### Requirement: Task runner starts on main-thread app boot
The system SHALL start the task runner from the main-thread entry point (`main.cljs`) on `DOMContentLoaded`; it SHALL NOT start from the Service Worker `activate` event. The task loop SHALL begin once memory is loaded (`specs/learner-data-memory/spec.md`), so that its queries do not compete with the load; a task that is due at start SHALL wait until then. A stop that comes before the loop has begun SHALL keep the loop from beginning. When the index the queue selects its tasks by cannot be built, the failure SHALL be logged and the loop SHALL NOT begin.

#### Scenario: Due tasks wait for memory
- **WHEN** the app starts with a task that is due
- **THEN** the task runs once memory is loaded, and not before

#### Scenario: A stop while memory loads
- **WHEN** the task runner is stopped while memory loads, as a failed start of the app stops what it started
- **THEN** the task loop does not begin once memory is loaded

#### Scenario: The queue's index cannot be built
- **WHEN** building the queue's index fails at start
- **THEN** the failure is logged and no task runs

#### Scenario: Task loop stops when tab closes
- **WHEN** the last tab running the app is closed
- **THEN** the task loop stops naturally (no background execution)
- **AND** pending tasks resume the next time the app is opened
