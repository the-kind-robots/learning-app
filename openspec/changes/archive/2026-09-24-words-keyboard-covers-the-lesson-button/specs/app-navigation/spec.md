## MODIFIED Requirements

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
