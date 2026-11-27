## MODIFIED Requirements

### Requirement: Today's stars open the statistics
Outside the statistics screen — on home, the words and the themes screens and in a lesson — today's stars in the shell bar SHALL be a button that opens the statistics screen. The statistics screen SHALL close by ✕ in the corner, by `Escape` and by the browser's Back, to the screen it was opened from, as it was left (app-navigation, Requirement: The open windows are a stack).

#### Scenario: Opening from home
- **WHEN** a learner with lessons today taps the stars on home
- **THEN** the statistics screen is on display

#### Scenario: Stars in a lesson
- **WHEN** a learner with lessons today taps the stars in a lesson
- **THEN** the statistics screen is on display over the lesson

#### Scenario: Closing over a lesson
- **WHEN** a learner with lessons today has typed part of an answer in a lesson, opens the statistics and closes them
- **THEN** the lesson is on display on the same trial, with the typed part in its answer field

#### Scenario: Closing
- **WHEN** the learner presses `Escape` on the statistics screen
- **THEN** home is on display
