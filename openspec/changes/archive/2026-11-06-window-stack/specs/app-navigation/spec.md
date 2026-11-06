## MODIFIED Requirements

### Requirement: Every screen but home closes from the corner
The words, lesson, themes and statistics screens SHALL each carry one close control (✕), labelled «Закрыть», in the top-right corner of the shell bar — the control the themes screen carries. The home screen SHALL carry no close control. Closing a screen SHALL take it off the stack of open windows and show the window beneath it (Requirement: The open windows are a stack); what leaving a screen does to that screen's own work belongs to the screen. No screen SHALL carry a second control of its own that leaves it for home.

#### Scenario: A close control on each screen but home
- **WHEN** the words, lesson or themes screen is on display
- **THEN** exactly one «Закрыть» control is in the top-right corner
- **AND** on the home screen there is none

#### Scenario: Closing the words screen
- **WHEN** the user taps «Закрыть» on the words screen opened from home
- **THEN** the home screen is on display

#### Scenario: Closing an active lesson
- **WHEN** the user taps «Закрыть» during a lesson started from home
- **THEN** the home screen is on display

#### Scenario: Closing a lesson started from the words screen
- **WHEN** the user starts a lesson from the words screen and taps «Закрыть» in it
- **THEN** the words screen is on display
- **AND** «Закрыть» on it shows the home screen

## REMOVED Requirements

### Requirement: Home has no in-app screen behind it
**Reason**: The open windows are a stack (#789): a screen opened from another lies over it rather than taking its place, so Back from a screen is the window beneath, not always home.
**Migration**: Requirement: The open windows are a stack.

## ADDED Requirements

### Requirement: The open windows are a stack
The browser history SHALL hold the open windows as a stack with the home screen at its bottom (ADR-0026). Opening a screen SHALL lay it over the window on display, unless it is open already beneath: then the windows above it SHALL come off, so no screen is open twice. Closing a screen — ✕, `Escape`, «ЗАКОНЧИТЬ» at the end of a lesson — and the browser's Back SHALL take the window on display off and show the one beneath, and Back on home SHALL leave the app. Going home by an in-app way — choosing a collection, the word list's way to add a word — SHALL take every window above home off. A screen the app is opened on directly SHALL have home beneath it. A reload SHALL keep the stack. The word mark opens the settings (interface-language, Requirement: The learner picks the interface language) and changes no screen.

#### Scenario: Back from a screen
- **WHEN** the user opens the words screen from home and presses the browser's Back
- **THEN** the home screen is on display

#### Scenario: Back after closing a screen
- **WHEN** the user opens the words screen from home, closes it, and presses the browser's Back on home
- **THEN** the words screen is not shown and the app leaves home for the page before it

#### Scenario: A screen opened from another screen
- **WHEN** the user opens the words screen from home, starts a lesson from it, and presses the browser's Back
- **THEN** the words screen is on display
- **AND** a second Back shows the home screen

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
