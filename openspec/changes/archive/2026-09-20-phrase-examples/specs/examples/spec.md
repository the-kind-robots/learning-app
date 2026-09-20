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
When the target of generation is a phrase, the generated German sentence SHALL contain the whole construction, and `structure` SHALL represent it as one item per sentence word it annotates — never one item spanning several words — each carrying the whole phrase as its `dictionaryForm` and the same Russian gloss the sentence was generated for, so every annotated word gets a `wordIndex` of its own. An item whose `usedForm` spans more than one word of the sentence SHALL be rejected. A function word of the construction that the inflected sentence does not carry MAY be absent from `structure`. The construction MAY appear inflected and rearranged by German word order, and its words need not be adjacent in the sentence. A word repeated inside the construction SHALL keep an item per occurrence: a repeat is legitimate when the repeated word belongs to the phrase target, and a repeated `{usedForm, dictionaryForm}` pair SHALL still be rejected otherwise, which is what keeps a separable verb from annotating the preposition that shares its prefix's spelling. A phrase that is already a complete sentence SHALL be placed in a sentence that extends or embeds it rather than returned verbatim. A generated example that does not satisfy this SHALL be rejected as invalid and regenerated, as one missing its target lemma already is.

#### Scenario: Inflected phrase inside a sentence
- **WHEN** an example is generated for the phrase "den Kopf verlieren"
- **THEN** the sentence contains the construction inflected, such as "Er verliert den Kopf."
- **AND** each word of the construction the sentence carries — "den", "Kopf", "verliert" — is its own `structure` item with `dictionaryForm` "den Kopf verlieren" and the same Russian gloss, and none of them spans more than one word

#### Scenario: Phrase split by German word order
- **WHEN** an example is generated for the phrase "auf jeden Fall"
- **THEN** a sentence such as "Ich komme auf jeden Fall mit." is accepted although the construction sits inside the clause
- **AND** each word of the phrase has its own `structure` item, so each carries its own `wordIndex`

#### Scenario: A word repeated inside the construction
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit"
- **THEN** the example is accepted although "Zeit" occurs twice in the construction
- **AND** each occurrence has its own `structure` item and its own `wordIndex`

#### Scenario: A repeat that does not belong to the target is still rejected
- **WHEN** the target is the separable verb "aufpassen" and `structure` carries the sentence's preposition "auf" beside the detached prefix "auf", both with `dictionaryForm` "aufpassen"
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: Phrase that is already a sentence
- **WHEN** an example is generated for a phrase that is itself a complete sentence
- **THEN** the generated sentence extends or embeds it into a turn rather than repeating it unchanged

#### Scenario: Construction missing from the output
- **WHEN** the generated sentence omits part of the construction, or no `structure` item names the whole phrase as its `dictionaryForm`
- **THEN** the example is rejected as invalid and generation is retried, and the retry is told the same rule in the same words the prompt states it in
