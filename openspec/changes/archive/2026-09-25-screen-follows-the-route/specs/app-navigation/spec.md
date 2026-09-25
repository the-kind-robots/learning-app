## ADDED Requirements

### Requirement: A screen left before it loads stays left
The screen on display SHALL be the one the address names, from the moment the address changes. A screen opened before its data has been read SHALL show nothing of its previous visit — no rows, no query, no open dialog, no previous lesson — until its own read lands. A data read that finishes after the user has left its screen SHALL change nothing visible: it neither brings its own screen back nor alters the one on display.

#### Scenario: Closing the words screen before its list is read
- **WHEN** the user opens the words screen from home and closes it before the list has been read, and the read finishes after home is on display
- **THEN** the home screen stays on display at `/home`
- **AND** the words screen does not appear

#### Scenario: A screen appears before its read lands
- **WHEN** the user opens the words screen and its list has not been read yet
- **THEN** the words screen is on display with its close control, and home is not

#### Scenario: Reopening the words screen after a search
- **WHEN** the user searched the words screen, left it, and opens it again before the list has been read
- **THEN** neither the previous query nor its result is on screen
- **AND** once the list is read it shows the words with an empty search field
