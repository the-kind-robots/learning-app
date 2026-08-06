## ADDED Requirements

### Requirement: A legacy database is adopted, never stranded or clobbered
On startup, before opening the database, the backend SHALL move a database found at the legacy location into the configured path when the target does not exist, crash-safely. When both exist it SHALL prefer the target, leave the legacy file in place and log loudly. The move and the path-reading SHALL ship in one artifact.

#### Scenario: First boot after the path change
- **WHEN** the configured path is empty and the legacy file exists
- **THEN** the data moves, the event is logged, and existing accounts keep working

#### Scenario: Rollback then roll forward
- **WHEN** both the legacy and the target exist
- **THEN** the target wins, nothing is deleted, and the log names both paths for the operator
