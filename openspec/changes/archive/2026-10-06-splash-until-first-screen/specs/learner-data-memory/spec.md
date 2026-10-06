## MODIFIED Requirements

### Requirement: The app opens on a splash until memory is loaded
The app SHALL show the server's splash, and nothing else, until memory holds everything user-db held at start; then the screen asked for SHALL replace it, with every word's retention, and nothing SHALL be shown between the splash and that screen. When reading the learner's data at start fails, the app SHALL say so in place of the splash and ask for a reload, and SHALL NOT read it again.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** the server's splash is on display until memory is loaded
- **AND** the screen asked for replaces it then, retention included, with nothing shown in between

#### Scenario: The data cannot be read
- **WHEN** reading the learner's data fails at start
- **THEN** «Не получается прочитать данные на устройстве. Перезагрузите страницу.» is shown in place of the splash
- **AND** nothing reads the data again until the page is reloaded
