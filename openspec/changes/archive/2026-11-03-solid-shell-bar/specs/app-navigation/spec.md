## ADDED Requirements

### Requirement: The shell bar is solid on every screen
The shell bar SHALL be solid and white on every screen, across the full width of the window. Content that scrolls up SHALL pass under the bar and SHALL NOT show through it. The bar SHALL look the same at rest and after scrolling. The bar SHALL be the same height on every screen. The middle of the bar, between the word mark and the actions, SHALL be one slot for indicators that apply to any screen; it SHALL NOT change the bar's height.

#### Scenario: Scrolling a long list of themes on a phone
- **WHEN** the user scrolls a list of themes longer than the screen
- **THEN** the tiles pass under the shell bar and none shows through it
- **AND** the bar looks as it did before the scroll

#### Scenario: Going from screen to screen
- **WHEN** the user opens home, the words, the themes and a lesson in turn
- **THEN** the shell bar is the same height on each
