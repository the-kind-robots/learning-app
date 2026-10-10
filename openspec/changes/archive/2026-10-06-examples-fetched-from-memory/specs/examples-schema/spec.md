## ADDED Requirements

### Requirement: An example request carries the collection's name
The system SHALL send, with an example request for a pair in a named collection, that collection's name as it stands in memory when the request is sent, so the backend can inject it into the prompt. The example the answer brings SHALL be stored with that collection's `collection-id`. A request for a pair in no collection SHALL carry no collection name.

#### Scenario: A request for a themed pair
- **WHEN** an example is requested for word W in collection T named «Поездка»
- **THEN** the request carries `context=Поездка`
- **AND** the stored example carries `collection-id = T`

#### Scenario: A request outside every collection
- **WHEN** an example is requested for word W in no collection
- **THEN** the request carries no `context` parameter

## MODIFIED Requirements

### Requirement: Cross-collection re-fetch generates context-specific example
The system SHALL generate a new context-specific example when an existing word is added to a named collection that does not yet have an example for that `(word-id, collection-id)` pair.

#### Scenario: Existing word added to a new themed collection
- **WHEN** a word already exists in vocabulary
- **AND** the user adds it to a named collection T that has no example for that `(word-id, T)` pair
- **THEN** an example request is sent for the word with T's name
- **AND** the resulting stored example carries `collection-id = T`

#### Scenario: No duplicate fetch when example already exists
- **WHEN** the user adds an existing word to a named collection that already has an example for that `(word-id, collection-id)` pair
- **THEN** no example request is sent for that pair

## REMOVED Requirements

### Requirement: Example fetch task carries collection-id and collection-name

**Reason**: There is no task payload. The request reads the collection from memory when it is sent.
**Migration**: Requirement: An example request carries the collection's name.
