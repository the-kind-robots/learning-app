## MODIFIED Requirements

### Requirement: Today's stars open the statistics
Outside a lesson and outside the statistics screen, today's stars in the shell bar SHALL be a button that opens the statistics screen. In a lesson the stars SHALL NOT be a button. The statistics screen SHALL close by ✕ in the corner, by `Escape` and by the browser's Back, to the screen it was opened from (app-navigation, Requirement: The open windows are a stack).

#### Scenario: Opening from home
- **WHEN** a learner with lessons today taps the stars on home
- **THEN** the statistics screen is on display

#### Scenario: Stars in a lesson
- **WHEN** a learner with lessons today is in a lesson
- **THEN** the stars are not a button

#### Scenario: Closing
- **WHEN** the learner presses `Escape` on the statistics screen
- **THEN** home is on display
