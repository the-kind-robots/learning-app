## ADDED Requirements

### Requirement: The home screen starts a lesson from the keyboard

`Alt`+`Enter` (`Option`+`Enter` on a Mac) on the home screen SHALL start the
lesson — the same action the `НАЧАТЬ УРОК` button performs. The handler SHALL
belong to the home page, so the keystroke works wherever the focus sits on that
screen and has no meaning on any other screen. The keystroke's default action
SHALL be suppressed, so it starts one lesson. When the vocabulary is empty the
lesson footer is hidden, and the keystroke SHALL do nothing.

#### Scenario: Alt+Enter from the word field

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** the German word field has focus
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens
- **AND** no word is added

#### Scenario: Alt+Enter from the translation field

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** the translation field has focus
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens
- **AND** the form is not submitted and the field takes no newline

#### Scenario: Alt+Enter outside the fields

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** focus is on one of the screen's buttons rather than on a field
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens

#### Scenario: An empty vocabulary offers no lesson

- **WHEN** the home screen is open with an empty vocabulary, so the lesson
  footer is hidden
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** nothing happens and the user stays on the home screen

#### Scenario: The screen's other keystrokes keep their meaning

- **WHEN** the user presses `Enter` on the German word field, `Ctrl`+`Enter` or
  `Cmd`+`Enter` on the translation field, or `Escape` with suggestions open
- **THEN** each keeps the behaviour it already had — picking the highlighted
  suggestion or moving on to the translation, submitting the form, dismissing
  the suggestions
