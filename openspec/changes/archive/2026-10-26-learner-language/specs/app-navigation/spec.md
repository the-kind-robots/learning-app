## MODIFIED Requirements

### Requirement: Home has no in-app screen behind it
The browser history SHALL hold the home screen and at most one other app screen above it. A screen opened from home SHALL add one entry; a screen opened from another screen SHALL take that screen's entry; reaching home by any in-app way — closing a screen, finishing a lesson, choosing a collection — SHALL return to the home entry beneath. A screen the app is opened on directly SHALL get a home entry beneath it. Back SHALL therefore take a screen to home, and Back on home SHALL leave the app rather than show another app screen. The word mark opens the settings (interface-language, Requirement: The learner picks the interface language) and changes no screen.

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
