## MODIFIED Requirements

### Requirement: Only Ctrl+Enter in the answer field checks the answer
`Ctrl`+`Enter` in the answer field — `Cmd`+`Enter` on a Mac — SHALL check the answer, as the ПРОВЕРИТЬ button does. A plain Enter in the field SHALL NOT check it: while the field shows the textarea, Enter SHALL break the line, as in any text field, and a phone's on-screen keyboard SHALL offer its ordinary Enter key there, not a Done key. A line break SHALL count in the answer as a space.

#### Scenario: Ctrl+Enter after typing
- **WHEN** the learner types an answer and presses `Ctrl`+`Enter`
- **THEN** the answer is checked

#### Scenario: Enter after typing
- **WHEN** the learner types an answer and presses Enter
- **THEN** the answer is not checked, and the field holds what was typed with a line break after it

#### Scenario: An answer on two lines
- **WHEN** the learner types «der», presses Enter, types «Hund» and presses `Ctrl`+`Enter` on the word «der Hund»
- **THEN** the answer is right

#### Scenario: Two Enters at the end of a lesson
- **WHEN** the learner has answered the last trial right and presses Enter twice
- **THEN** a new lesson is on display, its first trial not checked
