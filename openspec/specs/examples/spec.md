# examples Specification

## Purpose
Define how example sentences are generated and stored for vocabulary words, including task creation for fetching examples.

## Requirements
### Requirement: Example documents are stored
The system SHALL store example documents defined in `specs/data-model/spec.md`.

#### Scenario: Store example document
- **WHEN** an example is fetched for a word
- **THEN** an example document is created matching the example document shape in `specs/data-model/spec.md`

### Requirement: Example fetch tasks are created on word creation
The system SHALL create an example-fetch task whenever a word is created.

#### Scenario: Word creation triggers example-fetch task
- **WHEN** a word is added
- **THEN** an example-fetch task document is persisted for that word via the examples module

