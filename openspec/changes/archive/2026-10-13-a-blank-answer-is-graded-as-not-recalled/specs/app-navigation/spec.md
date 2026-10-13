## MODIFIED Requirements

### Requirement: A double click acts once
The second and later clicks of a series of clicks — a double click, a quick second tap on a phone — SHALL do nothing: a series is one activation of the control it started on. A click made from the keyboard is not part of a series.

#### Scenario: Double click on «НАЧАТЬ УРОК»
- **WHEN** the user double-clicks «НАЧАТЬ УРОК» on home
- **THEN** the lesson is on display with its first trial waiting for an answer
- **AND** no answer is checked and no review is recorded

#### Scenario: Double click on the corner ✕
- **WHEN** the user double-clicks «Закрыть» on a screen
- **THEN** home is on display
- **AND** no other screen is opened

#### Scenario: Double click on «ДАЛЕЕ»
- **WHEN** the user double-clicks «ДАЛЕЕ» in a lesson
- **THEN** the lesson shows the next trial waiting for its answer
- **AND** no answer is checked and no review is recorded
