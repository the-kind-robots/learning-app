## MODIFIED Requirements

### Requirement: Phrase and translation inputs are multi-line
Both the German input and the translation input SHALL be auto-growing multi-line fields: single-row when compact, growing with content up to a maximum height, then scrolling internally. Auto-growing SHALL work on Safari (JS fallback when `field-sizing: content` is unsupported). In a phrase, line breaks SHALL be visual only and SHALL be collapsed to single spaces on submit; in the translation of a word each line SHALL be a translation of its own (`specs/data-model/spec.md`).

#### Scenario: Long phrase grows the field
- **WHEN** the user pastes a phrase longer than one visual line
- **THEN** the field grows to show the content up to the maximum height without horizontal scrolling
