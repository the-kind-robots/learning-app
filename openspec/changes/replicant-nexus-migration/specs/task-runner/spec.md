## MODIFIED Requirements

### Requirement: Task runner starts on main-thread app boot
The system SHALL start the task runner loop from the main-thread entry point (`main.cljs`) on `DOMContentLoaded`; it SHALL NOT start from the Service Worker `activate` event.

#### Scenario: Task runner starts with the app
- **WHEN** the main-thread app boots
- **THEN** `tasks/start!` is called during initialisation
- **AND** the task loop begins processing pending tasks

#### Scenario: Task loop stops when tab closes
- **WHEN** the last tab running the app is closed
- **THEN** the task loop stops naturally (no background execution)
- **AND** pending tasks resume the next time the app is opened
