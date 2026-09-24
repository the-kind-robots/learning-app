# app-navigation Specification

## Purpose
Define how a screen is closed and what the browser's Back does across the app.

## Requirements

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

### Requirement: The words screen shows no heading and no back button
The words screen SHALL show neither a back button nor a visible heading; the rows start under the shell bar with a gap. With the keyboard closed, its search field SHALL sit at the foot of the screen, directly above the lesson button and as wide as it. Where the browser lets the keyboard overlay the page, the open keyboard SHALL cover the lesson button, which stays at the bottom of the screen, and the search field SHALL sit directly above the keyboard. A browser that cannot overlay the keyboard MAY carry the search field and the lesson button above it together. The screen SHALL keep the level-one heading «Мои слова» for assistive technology, visually hidden.

#### Scenario: Opening the words screen
- **WHEN** the words screen is on display with words in the vocabulary
- **THEN** no back button is present and no heading text is visible
- **AND** a screen reader finds the level-one heading «Мои слова»

#### Scenario: The search field is above the lesson button
- **WHEN** the words screen is on display with words in the vocabulary and no keyboard is open
- **THEN** the search field is directly above «Начать урок», with the same left and right edges
- **AND** the first row is below the shell bar, not touching it

#### Scenario: Typing a query on a phone
- **WHEN** the user focuses the search field on a phone whose browser overlays the keyboard, and the keyboard opens
- **THEN** the search field stays on screen directly above the keyboard
- **AND** «Начать урок» stays at the bottom of the screen, behind the keyboard

### Requirement: Home has no in-app screen behind it
The browser history SHALL hold the home screen and at most one other app screen above it. A screen opened from home SHALL add one entry; a screen opened from another screen SHALL take that screen's entry; reaching home by any in-app way — closing a screen, finishing a lesson, choosing a collection, the word mark — SHALL return to the home entry beneath. A screen the app is opened on directly SHALL get a home entry beneath it. Back SHALL therefore take a screen to home, and Back on home SHALL leave the app rather than show another app screen.

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
