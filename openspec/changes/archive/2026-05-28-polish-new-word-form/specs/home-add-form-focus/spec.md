# home-add-form-focus Specification

## ADDED Requirements

### Requirement: Enter on the German word input picks the highlighted suggestion
The system SHALL apply the highlighted autocomplete suggestion when the user presses Enter while the suggestions dropdown is open and has an active item. Default form submission SHALL be suppressed in this case.

#### Scenario: Enter confirms the active suggestion
- **WHEN** the suggestions dropdown is visible
- **AND** an item is active (selected via ArrowDown/ArrowUp or auto-highlighted)
- **AND** the user presses Enter while the German input has focus
- **THEN** default form submission is prevented
- **AND** the input value is set to the active suggestion's lemma
- **AND** the suggestions dropdown closes
- **AND** focus moves to the translation input

#### Scenario: Enter without an active suggestion submits the form
- **WHEN** the dropdown is closed or has no active item
- **AND** the user presses Enter
- **THEN** the form submits normally (no interception)

### Requirement: Blur on the German word input dismisses the suggestions
The system SHALL clear the suggestions dropdown when the German word input loses focus, without racing the click that picks a suggestion.

#### Scenario: Clicking outside dismisses suggestions
- **WHEN** the suggestions dropdown is visible
- **AND** the user clicks outside the input and the suggestion list
- **THEN** the input loses focus
- **AND** the suggestions dropdown is cleared

#### Scenario: Clicking a suggestion picks it
- **WHEN** the suggestions dropdown is visible
- **AND** the user clicks a suggestion item
- **THEN** the suggestion is applied (no race with the blur clear)
- **AND** the input value is set to the lemma
- **AND** focus moves to the translation input

### Requirement: Form buttons display a visible focus ring
The system SHALL render a visible focus indicator on every interactive button reachable by keyboard from the home screen — including `ДОБАВИТЬ`, `НАЧАТЬ УРОК`, `Список слов`, and the collections grid corner icon.

#### Scenario: Tabbing through the form shows focus on each button
- **WHEN** a keyboard user tabs through the home screen
- **THEN** each button receives a visible focus ring while focused
- **AND** the focus ring uses the project accent color (or an equally contrasting outline) so it is clearly distinguishable from the unfocused state
