## ADDED Requirements

### Requirement: The splash is replaced only once the fonts are loaded
The app SHALL keep the server's splash on display until the regular and the bold Nunito are loaded for Cyrillic and Latin text, or until one second has passed since start, whichever comes first; the screen that replaces the splash SHALL be drawn in Nunito from its first paint when the fonts loaded within that second. After the splash is replaced no font file SHALL be requested on behalf of the first screen.

#### Scenario: The first screen appears in Nunito
- **WHEN** the reader opens the app
- **THEN** at the moment the splash is replaced the regular and the bold Nunito are loaded
- **AND** no font file is requested after that moment

#### Scenario: Fonts that do not arrive do not hold the start
- **WHEN** the font files are not loaded one second after start
- **THEN** the splash is replaced by the screen anyway
