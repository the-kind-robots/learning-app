# screen-transitions Specification

## Purpose
How a screen change moves on a phone: depth slides, the lesson fades through, the next trial slides (ADR-0024).

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

### Requirement: Screens change at once where motion is not wanted

A window 700 px wide or wider, a system set to reduce motion and a browser without the View Transition API SHALL change screens at once, with no transition.

#### Scenario: A desktop window

- **WHEN** the user taps «Список слов» in a window 1280 px wide
- **THEN** the words screen replaces home at once

#### Scenario: Reduced motion

- **WHEN** the system asks for reduced motion and the user taps «Список слов» on a phone
- **THEN** the words screen replaces home at once
