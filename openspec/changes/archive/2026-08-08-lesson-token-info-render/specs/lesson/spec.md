# lesson Delta

## ADDED Requirements

### Requirement: Answer token click opens token-info card

The lesson UI SHALL open a token-info card when the user activates an annotated word in the revealed correct answer. The card SHALL reflect the word's vocabulary state and offer the matching action.

#### Scenario: Unknown word

- **WHEN** the user clicks an annotated word whose dictionary form is not in the vocabulary
- **THEN** a card opens showing the dictionary form and translation
- **AND** the card offers an add-to-dictionary action that saves the word with the translation

#### Scenario: Known word missing this translation

- **WHEN** the user clicks an annotated word that exists in the vocabulary without this translation
- **THEN** the card offers an add-translation action that merges the translation into the word

#### Scenario: Known word with translation

- **WHEN** the user clicks an annotated word already stored with this translation
- **THEN** the card shows the word as already in the dictionary and dismisses itself
