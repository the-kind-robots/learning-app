## ADDED Requirements

### Requirement: The answer panel rises from the bottom

On a phone, the panel that a checked answer brings — «Правильно!» or «Ваш ответ / Правильно» with «ДАЛЕЕ» — SHALL rise from the bottom edge into its place over 240 ms. On a wrong answer the line with the learner's answer SHALL then shake sideways once, settling within 320 ms.

#### Scenario: A right answer

- **WHEN** the user checks a right answer on a phone
- **THEN** the panel «Правильно!» rises from the bottom

#### Scenario: A wrong answer

- **WHEN** the user checks a wrong answer on a phone
- **THEN** the panel rises from the bottom
- **AND** the line with the user's answer shakes once the panel is up

### Requirement: The word dialog rises from the bottom

On a phone, opening a word from the word list SHALL raise its dialog from the bottom edge into its place over 240 ms while the backdrop behind it fades in.

#### Scenario: Opening a word

- **WHEN** the user taps a word in the word list on a phone
- **THEN** its dialog rises from the bottom and the backdrop fades in

### Requirement: Words added since the list was last opened are highlighted

Words added on home since the word list was last opened SHALL be highlighted when the list is next opened: each such row comes in from slightly above with a green tint that fades to the row's own colour within a second. That opening SHALL forget them, so the next one highlights none of them. A reload forgets them too. On a phone only; elsewhere the rows show without it.

#### Scenario: A word added on home

- **WHEN** the user adds «der Hund» on home on a phone and opens the word list
- **THEN** the row «der Hund» is highlighted and the other rows are not

#### Scenario: The list opened again

- **WHEN** the user leaves the word list and opens it again without adding a word
- **THEN** no row is highlighted

### Requirement: A new collection's tile grows in its place

On a phone, creating a collection SHALL grow its tile from smaller in its place while the tiles after it glide apart to make room, over 240 ms — the reverse of a deletion.

#### Scenario: Creating a collection

- **WHEN** the user creates a collection on the collections screen on a phone
- **THEN** its tile grows in its place and the tiles after it glide aside

### Requirement: Motion inside a screen stays still where motion is not wanted

A window 700 px wide or wider and a system set to reduce motion SHALL show the answer panel, the word dialog, a new word's row and a new collection's tile in place at once, with no motion.

#### Scenario: A wrong answer on a desktop

- **WHEN** the user checks a wrong answer in a window 1280 px wide
- **THEN** the panel is in place at once and nothing shakes

#### Scenario: Reduced motion

- **WHEN** the system asks for reduced motion and the user opens a word on a phone
- **THEN** its dialog is in place at once
