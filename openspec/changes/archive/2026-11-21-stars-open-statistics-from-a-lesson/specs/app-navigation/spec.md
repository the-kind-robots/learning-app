## MODIFIED Requirements

### Requirement: The open windows are a stack
The browser history SHALL hold the open windows as a stack with the home screen at its bottom (ADR-0026). Opening a screen SHALL lay it over the window on display, unless it is open already beneath: then the windows above it SHALL come off, so no screen is open twice. Closing a screen — ✕, `Escape`, «ЗАКОНЧИТЬ» at the end of a lesson — and the browser's Back SHALL take the window on display off and show the one beneath, and Back on home SHALL leave the app. The window shown by closing the ones above it SHALL be as it was left: what was typed, picked or scrolled in it, and what it showed of the learner's data. A screen opened SHALL be computed anew, one open beneath included (ADR-0029). Going home by an in-app way — choosing a collection, the word list's way to add a word — SHALL take every window above home off. A screen the app is opened on directly SHALL have home beneath it. A reload SHALL keep the stack. The word mark opens the settings (interface-language, Requirement: The learner picks the interface language) and changes no screen.

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

#### Scenario: A window shown again is as it was left
- **WHEN** the user searches the words screen for «Hu», opens the statistics from it and closes them
- **THEN** the words screen shows «Hu» in its search field and the words that match it

### Requirement: The words and themes screens open and close from the keyboard

On the home screen `Alt`+`W` SHALL open the words screen and `Alt`+`T` the themes screen; on home, the words and the themes screens and in a lesson `Alt`+`S` SHALL open the statistics, as today's stars do. Each SHALL act wherever the focus sits, read by the key's place so that any keyboard layout works, and SHALL do nothing where its button is not shown: `Alt`+`W` with an empty vocabulary, `Alt`+`S` without a lesson today, each on any other screen. `Escape` on the words or themes screen SHALL close it, as its corner ✕ does, unless a dialog or a menu is open: then `Escape` closes only the dialog or the menu. While a menu or a dialog is open no screen's hotkey SHALL act.

#### Scenario: Alt+W from the word field

- **WHEN** the vocabulary has a word and the learner presses Alt+W in the home word field
- **THEN** the words screen opens and nothing is typed into the field

#### Scenario: Alt+T on home

- **WHEN** the learner presses Alt+T on the home screen
- **THEN** the themes screen opens

#### Scenario: Alt+S on home

- **WHEN** a learner with a lesson today presses Alt+S in the home word field
- **THEN** the statistics screen opens and nothing is typed into the field

#### Scenario: Alt+S on the words or themes screen

- **WHEN** a learner with a lesson today presses Alt+S on the words or themes screen
- **THEN** the statistics screen opens over it

#### Scenario: Alt+S in a lesson

- **WHEN** a learner with a lesson today presses Alt+S in the answer field of a lesson
- **THEN** the statistics screen opens over the lesson and nothing is typed into the field

#### Scenario: Alt+S without a lesson today

- **WHEN** a learner with no lesson today presses Alt+S on home
- **THEN** the home screen stays

#### Scenario: Alt+W with an empty vocabulary

- **WHEN** the vocabulary is empty and the learner presses Alt+W on home
- **THEN** the home screen stays

#### Scenario: Escape on the words or themes screen

- **WHEN** the learner presses Escape on the words or themes screen
- **THEN** the home screen is shown

#### Scenario: Escape in the word dialog

- **WHEN** the word dialog is open and the learner presses Escape
- **THEN** the dialog closes and the words screen stays

#### Scenario: Escape in the settings menu

- **WHEN** the settings menu is open over the words screen and the learner presses Escape
- **THEN** the menu closes and the words screen stays
