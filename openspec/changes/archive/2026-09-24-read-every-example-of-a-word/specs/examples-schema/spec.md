## ADDED Requirements

### Requirement: A word's examples are read in full
The system SHALL return every example the device holds for a word when it reads that word's examples, however many there are. The read SHALL NOT stop at the storage layer's default result limit.

#### Scenario: A word with more examples than the default page size
- **WHEN** the device holds more examples for one word than the storage layer's default query limit
- **AND** the examples of that word are read
- **THEN** every one of them is returned

#### Scenario: The example past the default page is seen
- **WHEN** a word holds more examples than the storage layer's default query limit, one per collection
- **AND** the device asks whether it still needs an example for that word in the collection of the last of them
- **THEN** the answer is no
