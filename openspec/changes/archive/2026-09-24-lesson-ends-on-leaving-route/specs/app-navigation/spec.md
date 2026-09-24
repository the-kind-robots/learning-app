## MODIFIED Requirements

### Requirement: Every screen but home closes from the corner
The words, lesson and themes screens SHALL each carry one close control (✕), labelled «Закрыть», in the top-right corner of the shell bar — the control the themes screen carries. The home screen SHALL carry no close control. Closing a screen SHALL return home, the same way for every screen; what leaving a screen does to that screen's own work belongs to the screen. No screen SHALL carry a second control of its own that leaves it for home.

#### Scenario: A close control on each screen but home
- **WHEN** the words, lesson or themes screen is on display
- **THEN** exactly one «Закрыть» control is in the top-right corner
- **AND** on the home screen there is none

#### Scenario: Closing the words screen
- **WHEN** the user taps «Закрыть» on the words screen
- **THEN** the home screen is on display

#### Scenario: Closing an active lesson
- **WHEN** the user taps «Закрыть» during a lesson
- **THEN** the home screen is on display
