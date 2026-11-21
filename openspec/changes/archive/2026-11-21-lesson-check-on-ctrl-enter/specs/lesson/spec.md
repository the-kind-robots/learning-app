## RENAMED Requirements

- FROM: `### Requirement: Enter in the answer field checks the answer`
- TO: `### Requirement: Only Ctrl+Enter in the answer field checks the answer`

## MODIFIED Requirements

### Requirement: Only Ctrl+Enter in the answer field checks the answer
`Ctrl`+`Enter` in the answer field — `Cmd`+`Enter` on a Mac — SHALL check the answer, as the ПРОВЕРИТЬ button does. A plain Enter in the field, with or without Shift, SHALL NOT check it, and while the field shows the textarea SHALL NOT insert a line break; the Done key of a phone's on-screen keyboard SHALL do the same as a plain Enter. Enter during text composition SHALL only end the composition.

#### Scenario: Ctrl+Enter after typing
- **WHEN** the learner types an answer and presses `Ctrl`+`Enter`
- **THEN** the answer is checked

#### Scenario: Enter after typing
- **WHEN** the learner types an answer and presses Enter
- **THEN** the answer is not checked, and the field holds what was typed, with no line break added

#### Scenario: Two Enters at the end of a lesson
- **WHEN** the learner has answered the last trial right and presses Enter twice
- **THEN** a new lesson is on display, its first trial not checked

## ADDED Requirements

### Requirement: The answer field takes the focus no button holds
On a device with a fine pointer, whenever the current trial waiting for its answer comes on display and no button has the focus, the answer field SHALL receive it: when the lesson opens, when the next trial comes, when «ЕЩЁ УРОК» starts a new lesson, and when the learner returns to the current trial from a trial left behind.

#### Scenario: Back from a trial left behind
- **WHEN** the learner goes back from a word waiting for its answer to a trial left behind, and presses Enter there
- **THEN** the word is shown again, and the answer field has the focus
