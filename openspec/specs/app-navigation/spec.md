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

### Requirement: A double click acts once
The second and later clicks of a series of clicks — a double click, a quick second tap on a phone — SHALL do nothing: a series is one activation of the control it started on. A click made from the keyboard is not part of a series.

#### Scenario: Double click on «НАЧАТЬ УРОК»
- **WHEN** the user double-clicks «НАЧАТЬ УРОК» on home
- **THEN** the lesson is on display with its first trial waiting for an answer
- **AND** no answer is checked and no review is recorded

#### Scenario: Double click on the corner ✕
- **WHEN** the user double-clicks «Закрыть» on a screen
- **THEN** home is on display
- **AND** no other screen is opened

#### Scenario: Double click on «ДАЛЕЕ»
- **WHEN** the user double-clicks «ДАЛЕЕ» in a lesson
- **THEN** the lesson shows the next trial waiting for its answer
- **AND** no answer is checked and no review is recorded

### Requirement: A malformed query does not stop the app
An address whose query is not valid percent-encoding SHALL open the screen of its path. The query SHALL be dropped from the address.

#### Scenario: Opening home with a malformed query
- **WHEN** the app is opened at `/home?x=%`
- **THEN** home is on display
- **AND** the address is `/home`
- **AND** the screens open from home as usual

### Requirement: The words and themes screens open and close from the keyboard

On the home screen `Alt`+`W` SHALL open the words screen and `Alt`+`T` the themes screen, wherever the focus sits, read by the key's place so that any keyboard layout works. Each SHALL do nothing where its button is not shown: `Alt`+`W` with an empty vocabulary, both on any other screen. `Escape` on the words or themes screen SHALL close it, as its corner ✕ does, unless a dialog is open: then `Escape` closes only the dialog.

#### Scenario: Alt+W from the word field

- **WHEN** the vocabulary has a word and the learner presses Alt+W in the home word field
- **THEN** the words screen opens and nothing is typed into the field

#### Scenario: Alt+T on home

- **WHEN** the learner presses Alt+T on the home screen
- **THEN** the themes screen opens

#### Scenario: Alt+W with an empty vocabulary

- **WHEN** the vocabulary is empty and the learner presses Alt+W on home
- **THEN** the home screen stays

#### Scenario: Escape on the words or themes screen

- **WHEN** the learner presses Escape on the words or themes screen
- **THEN** the home screen is shown

#### Scenario: Escape in the word dialog

- **WHEN** the word dialog is open and the learner presses Escape
- **THEN** the dialog closes and the words screen stays

### Requirement: The words screen opens with the cursor in its search field

On a device with a fine pointer the words screen SHALL open with the focus in its search field. On a touch screen it SHALL NOT, so no keyboard rises.

#### Scenario: Words screen opened on a computer

- **WHEN** the learner opens the words screen on a computer
- **THEN** the search field has the focus and typed letters search

### Requirement: The shell bar is solid on every screen
The shell bar SHALL be solid and white on every screen, across the full width of the window. Content that scrolls up SHALL pass under the bar and SHALL NOT show through it. The bar SHALL look the same at rest and after scrolling. The bar SHALL be the same height on every screen. The middle of the bar, between the word mark and the actions, SHALL be one slot for indicators that apply to any screen; it SHALL NOT change the bar's height.

#### Scenario: Scrolling a long list of themes on a phone
- **WHEN** the user scrolls a list of themes longer than the screen
- **THEN** the tiles pass under the shell bar and none shows through it
- **AND** the bar looks as it did before the scroll

#### Scenario: Going from screen to screen
- **WHEN** the user opens home, the words, the themes and a lesson in turn
- **THEN** the shell bar is the same height on each

### Requirement: The shell bar shows today's lessons as stars
The middle of the shell bar SHALL show, on every screen, a star for each lesson passed today, counted from the local midnight. Every five gold stars SHALL be shown as one purple star instead. From five purple stars on, the purple stars SHALL be shown as their number followed by one purple star. With no lesson passed today the slot SHALL show no star. The stars SHALL carry an accessible name with the number of lessons passed today. A star that appears SHALL light up once, and SHALL not move when the user asked for reduced motion.

#### Scenario: Three lessons today
- **WHEN** a learner has passed three lessons today
- **THEN** the shell bar shows three gold stars

#### Scenario: Seven lessons today
- **WHEN** a learner has passed seven lessons today
- **THEN** the shell bar shows one purple star and two gold stars

#### Scenario: Twenty-six lessons today
- **WHEN** a learner has passed twenty-six lessons today
- **THEN** the shell bar shows «5», one purple star and one gold star

#### Scenario: A lesson passed shows at once
- **WHEN** a learner answers the final trial of a lesson correctly
- **THEN** the shell bar has one star more, on the lesson screen and on home

#### Scenario: A new day
- **WHEN** the learner passed lessons yesterday and none today
- **THEN** the shell bar shows no star

### Requirement: The main button keeps its place across screens
The big button at the bottom of a screen — «НАЧАТЬ УРОК» on home and on the words list, «ПРОВЕРИТЬ», «ДАЛЕЕ» and «ЕЩЁ УРОК» in a lesson — SHALL have one size and stand in one place on every screen that shows it, on a phone and on desktop alike, so that moving between those screens does not move it.

#### Scenario: From home through the words list into a lesson
- **WHEN** a learner on desktop opens the words list from home, starts a lesson from it and answers a trial
- **THEN** «НАЧАТЬ УРОК» on home, «НАЧАТЬ УРОК» on the words list, «ПРОВЕРИТЬ» and «ДАЛЕЕ» have the same size and position

#### Scenario: The same on a phone
- **WHEN** a learner on a phone does the same
- **THEN** the four buttons have the same size and position
