## ADDED Requirements

### Requirement: A pair misses the example of the learner's language

Whether memory has an example for a pair (Requirement: The device asks for the examples memory is missing) SHALL be decided over the examples in the learner's language only (`specs/examples-schema/spec.md`, Requirement: An example remembers its language, and a read sees the learner's), and a request SHALL carry that language. A change of the learner's language SHALL make the device look for a missing pair at once, as a change of memory's words does; the pairs a subject failed for in one language SHALL NOT keep the other language from being asked.

#### Scenario: An English learner's word
- **WHEN** the learner's language is English and memory holds a word with no example
- **THEN** the request for it carries `language=en`

#### Scenario: A switch to a language the word has no example in
- **WHEN** a word holds a Russian example and the learner's language becomes English
- **THEN** a request is sent for it with `language=en`
