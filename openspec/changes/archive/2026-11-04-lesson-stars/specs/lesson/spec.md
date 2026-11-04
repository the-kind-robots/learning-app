## ADDED Requirements

### Requirement: The end of a lesson offers one more
When the final trial of a lesson is answered correctly, the footer SHALL offer «ЕЩЁ УРОК» as its main button, focused, standing exactly where «ДАЛЕЕ» stands after any other trial, and «ЗАКОНЧИТЬ» as a quiet button on the line of «Правильно!», at its end. «ЕЩЁ УРОК» SHALL start a new lesson at once, on the lesson screen. «ЗАКОНЧИТЬ» SHALL show the home screen.

#### Scenario: The main button does not move
- **WHEN** a learner answers the last but one trial and then the final trial correctly, with answers of the same length
- **THEN** «ЕЩЁ УРОК» occupies the same place on screen that «ДАЛЕЕ» did

#### Scenario: One more lesson
- **WHEN** a learner answers the final trial correctly and activates «ЕЩЁ УРОК»
- **THEN** the first trial of a new lesson is on display
- **AND** the trials of the finished lesson are gone from the strip

#### Scenario: Finishing after the last trial
- **WHEN** a learner answers the final trial correctly and activates «ЗАКОНЧИТЬ»
- **THEN** the home screen is on display

### Requirement: A passed lesson is recorded
The system SHALL record a lesson as passed when its final trial is answered correctly, once per lesson, in the learner's data with the time it was passed. The record SHALL replicate with the account like the reviews do, and SHALL never be changed afterwards. A lesson left before its end SHALL record nothing.

#### Scenario: Lesson passed
- **WHEN** a learner answers the final trial of a lesson correctly
- **THEN** one passed lesson is recorded

#### Scenario: Lesson left early
- **WHEN** a learner closes a lesson before its final trial is answered
- **THEN** no passed lesson is recorded
