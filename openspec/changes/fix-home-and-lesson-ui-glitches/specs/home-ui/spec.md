## Purpose
Define stable home-screen UI behavior for add-word, word-list, and empty-state transitions across desktop and mobile layouts.

## ADDED Requirements

### Requirement: Home footer animation only plays for the first-word transition
The home screen SHALL avoid replaying the footer animation on every return to the home screen and only use it for the intended first-word transition.

#### Scenario: Returning to home after the user already has words
- **WHEN** the user returns to the home screen while the vocabulary list is already non-empty
- **THEN** the footer does not replay the first-word transition animation

### Requirement: Mobile add-word guidance does not cover the active input
The home screen SHALL keep mobile add-word guidance/help UI from covering the active word-entry input.

#### Scenario: Adding a word on mobile
- **WHEN** the user is entering a word on a mobile or coarse-pointer layout
- **THEN** any helper or guidance UI remains positioned so the active input stays visible and usable

### Requirement: Word-list editing remains visually stable
The home screen SHALL keep inline word editing visually stable instead of jittering or visibly reflowing during normal edit interactions.

#### Scenario: Editing a word in the list
- **WHEN** the user enters or uses inline edit mode for a word in the list
- **THEN** the row and surrounding list remain visually stable through the edit interaction

### Requirement: Deleting the last word transitions cleanly to the empty state
The home screen SHALL transition directly and smoothly to the empty-state view when the user deletes the final word in the list.

#### Scenario: Removing the final word
- **WHEN** the user deletes the last remaining word from the vocabulary list
- **THEN** the home screen transitions to the empty-state layout without a visible intermediate jerk or stale non-empty layout flash
