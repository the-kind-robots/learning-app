## MODIFIED Requirements

### Requirement: The words screen shows no heading and no back button
The words screen SHALL show neither a back button nor a visible heading; the rows start under the shell bar with a gap. Its search field SHALL sit in the bottom tray, directly above the lesson button and as wide as it. While a phone's keyboard is open, the tray SHALL stay above the keyboard, as the lesson's answer field does. The screen SHALL keep the level-one heading «Мои слова» for assistive technology, visually hidden.

#### Scenario: Opening the words screen
- **WHEN** the words screen is on display with words in the vocabulary
- **THEN** no back button is present and no heading text is visible
- **AND** a screen reader finds the level-one heading «Мои слова»

#### Scenario: The search field is above the lesson button
- **WHEN** the words screen is on display with words in the vocabulary
- **THEN** the search field is directly above «Начать урок», with the same left and right edges
- **AND** the first row is below the shell bar, not touching it

#### Scenario: Typing a query on a phone
- **WHEN** the user focuses the search field on a phone and the keyboard opens
- **THEN** the search field and the lesson button stay on screen above the keyboard
