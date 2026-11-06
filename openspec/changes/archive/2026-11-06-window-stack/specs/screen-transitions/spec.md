## MODIFIED Requirements

### Requirement: Going back slides the screen out to the right

On a phone, taking a screen other than the lesson off the stack of open windows SHALL slide it out to the right over 200 ms while the window beneath comes back from the left, home or any other. A collection picked on the collections screen SHALL return home the same way; home's heading SHALL already show the picked collection, with no motion of its own.

#### Scenario: Closing from the corner

- **WHEN** the user taps ✕ on the words screen on a phone
- **THEN** the words screen leaves to the right and home comes back from the left

#### Scenario: Picking a collection

- **WHEN** the user taps a collection on the collections screen on a phone
- **THEN** the collections screen leaves to the right and home comes back from the left with that collection's name as its heading

#### Scenario: The browser's Back

- **WHEN** the user goes back from the words screen with the browser's Back on a phone
- **THEN** the words screen leaves to the right and home comes back from the left

#### Scenario: Closing the statistics over the words screen

- **WHEN** the user taps ✕ on the statistics opened from the words screen on a phone
- **THEN** the statistics leave to the right and the words screen comes back from the left

#### Scenario: A back gesture the browser animates itself

- **WHEN** the browser draws its own animation for a back gesture
- **THEN** home appears with no transition of the app's own on top of it
