## MODIFIED Requirements

### Requirement: Every screen but home closes from the corner
The words, lesson, themes and statistics screens SHALL each carry one close control (✕), labelled «Закрыть», in the top-right corner of the shell bar — the control the themes screen carries. The home screen SHALL carry no close control. Closing a screen SHALL return home, the same way for every screen but the statistics, which SHALL return to the screen they were opened from; what leaving a screen does to that screen's own work belongs to the screen. No screen SHALL carry a second control of its own that leaves it for home.

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

### Requirement: Home has no in-app screen behind it
The browser history SHALL hold the home screen and at most one other app screen above it, and the statistics over that screen when they were opened from it (ADR-0026). A screen opened from home SHALL add one entry; a screen opened from another screen SHALL take that screen's entry, but the statistics SHALL add one entry over the screen they are opened from, and leaving them by ✕, `Escape` or Back SHALL return to it; reaching home by any in-app way — closing a screen, finishing a lesson, choosing a collection — SHALL return to the home entry beneath. A screen the app is opened on directly SHALL get a home entry beneath it. Back SHALL therefore take a screen to home, and Back on home SHALL leave the app rather than show another app screen. The word mark opens the settings (interface-language, Requirement: The learner picks the interface language) and changes no screen.

#### Scenario: Back from a screen
- **WHEN** the user opens the words screen from home and presses the browser's Back
- **THEN** the home screen is on display

#### Scenario: Back after closing a screen
- **WHEN** the user opens the words screen from home, closes it, and presses the browser's Back on home
- **THEN** the words screen is not shown and the app leaves home for the page before it

#### Scenario: A screen opened from another screen
- **WHEN** the user opens the words screen from home, starts a lesson from it, and presses the browser's Back
- **THEN** the home screen is on display

#### Scenario: Opened directly on a screen
- **WHEN** the app is opened at `/words` and the user presses the browser's Back
- **THEN** the home screen is on display

#### Scenario: Back from the statistics opened from a screen
- **WHEN** the user opens the words screen from home, opens the statistics from it, and presses the browser's Back
- **THEN** the words screen is on display
- **AND** a second Back shows the home screen

#### Scenario: Closing the statistics opened from a screen
- **WHEN** the user opens the collections from home, opens the statistics from them, and taps «Закрыть»
- **THEN** the collections screen is on display
