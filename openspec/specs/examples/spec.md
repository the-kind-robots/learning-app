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

### Requirement: Example fetch tasks are idempotent per word
The system SHALL ensure that requesting an example fetch for the same word does not create more than one active example-fetch task for that word.

#### Scenario: Repeated fetch request
- **WHEN** example fetch is requested twice for the same word
- **THEN** the same active example-fetch task is reused
- **AND** no duplicate active example-fetch task is created

#### Scenario: Concurrent fetch request
- **WHEN** two app instances request an example fetch for the same word at the same time
- **THEN** deterministic task identity prevents duplicate active example-fetch tasks
- **AND** both callers can continue without surfacing a conflict error to the user

### Requirement: Example fetch state is visible
The system SHALL expose each vocabulary word's example state as no example, fetching, failed, or ready.

#### Scenario: Word has no example or task
- **WHEN** a word has no usable example and no active or failed example-fetch task
- **THEN** the word's example state is no example

#### Scenario: Word has active fetch task
- **WHEN** a word has an active example-fetch task
- **THEN** the word's example state is fetching

#### Scenario: Word has failed fetch task
- **WHEN** a word has a failed example-fetch task and no usable example
- **THEN** the word's example state is failed

#### Scenario: Word has usable example
- **WHEN** a word has at least one usable example
- **THEN** the word's example state is ready

### Requirement: Users can manage word examples
The system SHALL let users make, retry, add, edit, replace, and delete examples for vocabulary words.

#### Scenario: User requests example
- **WHEN** the user requests an example for a word with no example
- **THEN** an idempotent example-fetch task is scheduled for that word

#### Scenario: User requests example when one is ready
- **WHEN** the user requests an example for a word with a usable example
- **THEN** no replacement example-fetch task is scheduled

#### Scenario: User retries failed fetch
- **WHEN** the user retries a failed example fetch
- **THEN** the failed fetch task is reset and scheduled again for the same word

#### Scenario: User deletes example
- **WHEN** the user deletes an example
- **THEN** the example is no longer used for the word
- **AND** no replacement fetch task is created automatically

### Requirement: User examples coexist with fetched examples
The system SHALL store user-owned examples separately from fetched examples and render a random usable example for lessons.

#### Scenario: User adds example when fetched example exists
- **WHEN** the user adds their own example for a word that already has a fetched example
- **THEN** the user example is stored as a separate example document
- **AND** the fetched example remains stored

#### Scenario: Lesson uses random usable example
- **WHEN** a lesson uses an example for a word with multiple usable examples
- **THEN** it picks one usable example at random

#### Scenario: Fetched example without valid structure
- **WHEN** a word only has fetched examples without valid sentence structure
- **THEN** those examples are not usable for lessons
- **AND** example fetch can be requested again for the word

### Requirement: User examples are unstructured
The system SHALL store user-owned examples without sentence structure.

#### Scenario: User example saved
- **WHEN** the user saves their own example
- **THEN** the example text and translation are stored exactly as entered
- **AND** no sentence-structure task is scheduled
- **AND** no structure is required

#### Scenario: Lesson renders user example
- **WHEN** a lesson renders a user example
- **THEN** the sentence and translation are shown
- **AND** word-level popups are not shown

### Requirement: Fetched examples require structure
The system SHALL store fetched example payloads tolerantly and require valid sentence structure only before fetched examples are used in lessons.

#### Scenario: Fetched example with source missing
- **WHEN** an example document has no `source`
- **THEN** the example is treated as fetched data

#### Scenario: Fetched example has valid structure
- **WHEN** a fetched example has valid sentence structure
- **THEN** it is usable for lesson rendering with word-level popups

#### Scenario: Fetched example has missing or outdated structure
- **WHEN** a fetched example has missing, invalid, or outdated sentence structure
- **THEN** it is not used for lesson rendering
- **AND** the stored document remains available for a future client that may understand it

#### Scenario: Fetched example has unknown extra fields
- **WHEN** a fetched example includes fields this client does not understand
- **THEN** those fields are preserved when the example is stored
- **AND** they do not prevent lesson rendering when required render fields are valid

