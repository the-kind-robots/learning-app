## ADDED Requirements

### Requirement: Suggestions feel instant after a typing pause
Suggestions SHALL appear within ~150 ms of a typing pause, and continuous typing SHALL NOT trigger a lookup per keystroke. Changes to this budget SHALL cite a measurement, not a guess.

#### Scenario: Pause after typing
- **WHEN** the user stops typing
- **THEN** suggestions appear within the budget

#### Scenario: Fast typing
- **WHEN** keystrokes arrive faster than the debounce
- **THEN** lookups coalesce and the visible list never blanks between answers
