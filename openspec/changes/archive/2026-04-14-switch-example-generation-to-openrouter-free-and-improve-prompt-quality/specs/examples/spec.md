## MODIFIED Requirements

### Requirement: Example generation provider path remains configurable
The system SHALL allow backend example generation to target a configurable provider/model path without changing the client-facing example contract.

#### Scenario: Switching backend example generation transport
- **WHEN** the backend provider for example generation is changed
- **THEN** the backend still returns the same logical example JSON structure used by the client
- **AND** provider/model selection is controlled by backend configuration rather than hard-coded directly into the prompt text

### Requirement: Example generation prompt remains quality-controlled
The system SHALL use a prompt structure that explicitly constrains example grammar, translation quality, and output schema compliance.

#### Scenario: Generating an example for a vocabulary word
- **WHEN** the backend requests a generated example
- **THEN** the prompt clearly constrains the required fields and linguistic behavior
- **AND** the generated payload remains usable by the existing example persistence flow

### Requirement: Example generation uses the intended stored meaning
The system SHALL generate examples from the German word together with the intended Russian meaning already stored for that vocabulary item.

#### Scenario: Generating an example for an ambiguous or rare word
- **WHEN** the app requests an example for a vocabulary item
- **THEN** the backend includes both the German word and its intended Russian meaning in the model input
- **AND** the generator is constrained to that intended meaning instead of inventing a different sense

### Requirement: Obviously bad generated payloads are rejected before persistence
The system SHALL reject generated example payloads that are structurally present but clearly unusable.

#### Scenario: Model returns meta commentary or non-Russian glosses
- **WHEN** the model returns an example payload that contains meta commentary, non-Russian glosses, or malformed structure items
- **THEN** the backend treats that payload as invalid
- **AND** the invalid payload is not returned as a successful example result
