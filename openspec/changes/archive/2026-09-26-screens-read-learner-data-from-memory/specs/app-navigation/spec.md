## ADDED Requirements

### Requirement: A double click acts once
A screen appears in the task of the tap that opens it, so the second click of a double
click lands on the screen the first one opened. That second click SHALL do nothing: a
double click is one activation of the control it started on.

#### Scenario: Double click on «НАЧАТЬ УРОК»
- **WHEN** the user double-clicks «НАЧАТЬ УРОК» on home
- **THEN** the lesson is on display with its first trial waiting for an answer
- **AND** no answer is checked and no review is recorded

#### Scenario: Double click on the corner ✕
- **WHEN** the user double-clicks «Закрыть» on a screen
- **THEN** home is on display
- **AND** no other screen is opened
