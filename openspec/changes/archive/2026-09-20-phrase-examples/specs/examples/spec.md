## REMOVED Requirements

### Requirement: Example fetch tasks are created on word creation
**Reason**: The requirement's name and text scope task creation to words, which is the exclusion #371 reverses. A phrase is a vocabulary document like any other and now gets an example the same way, so the trigger is the creation of a vocabulary entry, not of a word. The collection context it carried is unchanged and is carried into the requirement that replaces it.

**Migration**: None. Tasks already queued keep their shape; only the set of entries that produce one grows.

## ADDED Requirements

### Requirement: Example fetch tasks are created on vocabulary entry creation
The system SHALL create an example-fetch task whenever a vocabulary entry is created, whether it is a word or a phrase, carrying the active collection context (see `specs/examples-schema/spec.md`).

#### Scenario: Word creation triggers example-fetch task
- **WHEN** a word is added
- **THEN** an example-fetch task document is persisted for that word via the examples module

#### Scenario: Phrase creation triggers example-fetch task
- **WHEN** a phrase is added
- **THEN** an example-fetch task document is persisted for that phrase via the examples module, with the same payload shape a word's task has

### Requirement: A generated example for a phrase carries the whole construction
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction. It MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. An example whose sentence does not carry the whole construction SHALL be rejected as invalid and regenerated.

`structure` SHALL annotate the sentence word by word under the same rules whatever the target is, and SHALL NOT record which words belonged to the construction. Presence of a multi-word target is therefore checked against the sentence: every word of the target SHALL be present, matching either a word of the sentence or the `dictionaryForm` of some `structure` item, which is what carries inflection. A single-lemma target SHALL keep being checked against `structure`, by the `dictionaryForm` that names it.

#### Scenario: Inflected phrase inside a sentence
- **WHEN** an example is generated for the phrase "den Kopf verlieren"
- **THEN** a sentence such as "Er verliert den Kopf." is accepted
- **AND** "den" and "Kopf" are found in the sentence, and "verlieren" through the `structure` item whose `dictionaryForm` names it

#### Scenario: Phrase split by German word order
- **WHEN** an example is generated for the phrase "auf jeden Fall"
- **THEN** a sentence such as "Ich komme auf jeden Fall mit." is accepted although the construction sits inside the clause
- **AND** `structure` annotates the sentence's words under the ordinary rules, so "Fall" carries its own lemma and "auf" and "jeden" are left out as a preposition and a determiner

#### Scenario: A word the sentence says twice
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit"
- **THEN** the example is accepted although "Zeit" occurs twice
- **AND** each occurrence is its own `structure` item, annotated as the noun it is, with its own `wordIndex`

#### Scenario: Phrase that is already a sentence
- **WHEN** an example is generated for a phrase that is itself a complete sentence
- **THEN** the generated sentence extends or embeds it into a turn rather than repeating it unchanged

#### Scenario: Construction missing from the output
- **WHEN** the generated sentence carries only some of the construction's words
- **THEN** the example is rejected as invalid and generation is retried, and the retry is told the same rule in the same words the prompt states it in
