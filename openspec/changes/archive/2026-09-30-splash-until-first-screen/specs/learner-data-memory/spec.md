## MODIFIED Requirements

### Requirement: The app opens on a splash until the words and collections are read
The app SHALL show the server's splash, and nothing else, until the words and the collections are in memory, and SHALL then replace it with the screen asked for; nothing SHALL be shown between the splash and that screen; from then on adding a word, switching collections and every screen SHALL be available. The reviews and the examples SHALL load after the splash is gone. Until the reviews are in memory, the words screen SHALL list its words with a neutral retention mark in place of each word's retention, and a lesson opened in that time SHALL be drawn once they are.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** the server's splash is on display until the words and the collections are in memory
- **AND** the screen asked for replaces it then, with nothing shown in between

#### Scenario: The words screen before the reviews are read
- **WHEN** the words screen is opened after the splash and before the reviews are in memory
- **THEN** its words are listed with a neutral retention mark
- **AND** each word's retention is shown once the reviews are in memory
