# lesson Delta

## MODIFIED Requirements

### Requirement: Answer token click opens token-info card

The lesson UI SHALL open a token-info card when the user activates an annotated word in the revealed correct answer. The card SHALL render as a popover anchored to the activated word — not as a centered modal — with an arrow pointing at the word, and SHALL reflect the word's vocabulary state, offering the matching action.

#### Scenario: Unknown word

- **WHEN** the user clicks an annotated word whose dictionary form is not in the vocabulary
- **THEN** a popover opens anchored above the word (below when there is no room above), showing the dictionary form and translation
- **AND** the popover offers an add-to-dictionary action that saves the word with the translation

#### Scenario: Known word missing this translation

- **WHEN** the user clicks an annotated word that exists in the vocabulary without this translation
- **THEN** the popover offers an add-translation action that merges the translation into the word

#### Scenario: Known word with translation

- **WHEN** the user clicks an annotated word already stored with this translation
- **THEN** the popover shows the word as already in the dictionary and dismisses itself after a short delay

#### Scenario: Dismissal

- **WHEN** the popover is open
- **THEN** clicking outside it or pressing Escape closes it
- **AND** on hover-capable devices it auto-closes after an idle timeout unless the pointer or focus is inside it
- **AND** no modal chrome, backdrop dimming, or forced focus ring is presented
