## ADDED Requirements

### Requirement: Hinted words take the width of plain text
The revealed correct answer SHALL occupy the same width whether or not its words carry hints: a hinted word SHALL add no width of its own, at rest, on hover, on focus or while its hint is open.

#### Scenario: Sentence with hinted words
- **WHEN** the revealed answer shows a sentence with hinted words
- **THEN** its rendered width equals that of the same text without hints, within half a pixel

### Requirement: Continuing a lesson advances one trial
Activating the continue button («ДАЛЕЕ» or «ЗАКОНЧИТЬ») once SHALL act once: one press of Enter on the focused button, or one click, advances the lesson by exactly one trial and saves it without a conflict. Activations arriving while an advance is still being saved SHALL join that advance rather than start another.

#### Scenario: Enter on the continue button
- **WHEN** the continue button is focused after an answer and the user presses Enter once
- **THEN** the lesson shows the next trial, skipping none
- **AND** no lesson save fails

#### Scenario: Double click on the continue button
- **WHEN** the user double-clicks the continue button
- **THEN** the lesson advances by one trial
- **AND** no lesson save fails
