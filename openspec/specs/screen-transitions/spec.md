# screen-transitions Specification

## Purpose
How a screen change moves on a phone: depth slides, the lesson fades through, the next trial slides, a deleted collection closes its gap; and what moves inside a screen: the answer panel and the word dialog rise, a new word's row is highlighted, a new collection opens a gap (ADR-0024).

## Requirements

### Requirement: Going deeper slides the new screen in from the right

On a phone, a window narrower than 700 px, opening a screen other than the lesson SHALL slide the new screen in from the right edge over 240 ms while the screen being left moves a little to the left and darkens. The bar across the top SHALL stay where it is.

#### Scenario: Opening the words screen

- **WHEN** the user taps «Список слов» on home on a phone
- **THEN** the words screen enters from the right and home moves left behind it

#### Scenario: Opening the collections

- **WHEN** the user taps «Открыть наборы» on home on a phone
- **THEN** the collections screen enters from the right and home moves left behind it

### Requirement: Going back slides the screen out to the right

On a phone, returning to home from a screen other than the lesson SHALL slide that screen out to the right over 200 ms while home comes back from the left. A collection picked on the collections screen SHALL return the same way; home's heading SHALL already show the picked collection, with no motion of its own.

#### Scenario: Closing from the corner

- **WHEN** the user taps ✕ on the words screen on a phone
- **THEN** the words screen leaves to the right and home comes back from the left

#### Scenario: Picking a collection

- **WHEN** the user taps a collection on the collections screen on a phone
- **THEN** the collections screen leaves to the right and home comes back from the left with that collection's name as its heading

#### Scenario: The browser's Back

- **WHEN** the user goes back from the words screen with the browser's Back on a phone
- **THEN** the words screen leaves to the right and home comes back from the left

#### Scenario: A back gesture the browser animates itself

- **WHEN** the browser draws its own animation for a back gesture
- **THEN** home appears with no transition of the app's own on top of it

### Requirement: The lesson opens and closes by fading through

On a phone, entering or leaving the lesson SHALL fade the screen being left out and fade the next one in over 200 ms, with no sideways motion: the lesson fades in from slightly larger, and fades out growing slightly larger when it closes.

#### Scenario: Starting a lesson

- **WHEN** the user taps «НАЧАТЬ УРОК» on a phone
- **THEN** home fades out and the lesson fades in from slightly larger

#### Scenario: Closing a lesson

- **WHEN** the user closes the lesson with ✕ on a phone
- **THEN** the lesson fades out and home fades in

### Requirement: The next task replaces the current one sideways

On a phone, «ДАЛЕЕ» SHALL move the current task out to the left and bring the next task in from the right over 180 ms, while the rest of the lesson screen stays still.

#### Scenario: Moving to the next task

- **WHEN** the user taps «ДАЛЕЕ» after an answer on a phone
- **THEN** the task leaves to the left and the next one enters from the right

### Requirement: A deleted collection's neighbours close the gap

On a phone, deleting a collection SHALL shrink its tile away while the tiles after it glide into its place over 200 ms.

#### Scenario: Deleting a collection

- **WHEN** the user deletes a collection on the collections screen on a phone
- **THEN** its tile shrinks away and the tiles after it glide into the gap

### Requirement: Screens change at once where motion is not wanted

A window 700 px wide or wider, a system set to reduce motion and a browser without the View Transition API SHALL change screens at once, with no transition.

#### Scenario: A desktop window

- **WHEN** the user taps «Список слов» in a window 1280 px wide
- **THEN** the words screen replaces home at once

#### Scenario: Reduced motion

- **WHEN** the system asks for reduced motion and the user taps «Список слов» on a phone
- **THEN** the words screen replaces home at once

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
