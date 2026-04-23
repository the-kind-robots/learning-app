## ADDED Requirements

### Requirement: Example fetch tasks are idempotent per word
The system SHALL ensure that requesting example generation for the same word does not create more than one active example-fetch task for that word.

#### Scenario: Repeated generation request
- **WHEN** example generation is requested twice for the same word
- **THEN** the same active example-fetch task is reused
- **AND** no duplicate active example-fetch task is created

#### Scenario: Concurrent generation request
- **WHEN** two app instances request example generation for the same word at the same time
- **THEN** deterministic task identity prevents duplicate active example-fetch tasks
- **AND** both callers can continue without surfacing a conflict error to the user

### Requirement: Example generation state is visible
The system SHALL expose each vocabulary word's example state as no example, generating, failed, or ready.

#### Scenario: Word has no example or task
- **WHEN** a word has no preferred example and no active or failed example-fetch task
- **THEN** the word's example state is no example

#### Scenario: Word has active generation task
- **WHEN** a word has an active example-fetch task
- **THEN** the word's example state is generating

#### Scenario: Word has failed generation task
- **WHEN** a word has a failed example-fetch task and no preferred example
- **THEN** the word's example state is failed

#### Scenario: Word has preferred example
- **WHEN** a word has a preferred example
- **THEN** the word's example state is ready

### Requirement: Users can manage word examples
The system SHALL let users generate, retry, add, edit, replace, and delete examples for vocabulary words.

#### Scenario: User requests generation
- **WHEN** the user requests example generation for a word with no example
- **THEN** an idempotent example-fetch task is scheduled for that word

#### Scenario: User retries failed generation
- **WHEN** the user retries a failed example generation
- **THEN** the failed generation task is reset and scheduled again for the same word

#### Scenario: User deletes example
- **WHEN** the user deletes an example
- **THEN** the example is no longer used for the word
- **AND** no replacement generation task is created automatically

### Requirement: User examples coexist with generated examples
The system SHALL store user-owned examples separately from AI-generated examples and render the preferred example for lessons.

#### Scenario: User adds example when AI example exists
- **WHEN** the user adds their own example for a word that already has an AI-generated example
- **THEN** the user example is stored as a separate example document
- **AND** the user example becomes the preferred example
- **AND** the AI-generated example remains stored

#### Scenario: Lesson uses preferred example
- **WHEN** a lesson uses an example for a word
- **THEN** it uses the word's preferred example

### Requirement: User examples are unstructured
The system SHALL store user-owned examples without AI-generated sentence structure.

#### Scenario: User example saved
- **WHEN** the user saves their own example
- **THEN** the example text and translation are stored exactly as entered
- **AND** no sentence-structure generation task is scheduled
- **AND** the example structure status is absent

#### Scenario: Lesson renders unstructured user example
- **WHEN** a lesson renders a user example whose structure status is absent
- **THEN** the sentence and translation are shown
- **AND** word-level popups are not shown
