## MODIFIED Requirements

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
