## ADDED Requirements

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened and SHALL show a loading state there until its collections are available. A reload of the screen that is already open (after a sync pull) SHALL keep the current collections on screen until the new ones arrive, with no loading state in between.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen
- **THEN** the screen is on display with a loading state before its collections have been read
- **AND** the collections replace the loading state once they are available

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull completes
- **THEN** the current collections stay on screen until the reloaded ones replace them
- **AND** no loading state is shown in between

### Requirement: Collection cards own their touch gestures
A collection card SHALL take a long press as its own gesture: pressing a card SHALL NOT select its preview text or open the browser's callout, and a double tap on a card SHALL NOT zoom the page. Panning and pinching the screen stay with the browser.

#### Scenario: Long press on a card
- **WHEN** the user presses and holds a collection card on a touch screen
- **THEN** the card enters its editing state and no text on it is selected

#### Scenario: Quick double tap on a card
- **WHEN** the user taps a collection card twice in quick succession
- **THEN** the page does not zoom

### Requirement: A tap the browser cancels without movement still opens the collection
When the browser cancels a touch on a collection card (`pointercancel` with no `click` following), the card SHALL take it as a tap if the pointer travelled at most 10 px since it went down and the page scrolled at most 2 px in the meantime. A gesture SHALL fire at most once: a `click` the browser delivers after such a recovered cancel does nothing.

#### Scenario: Cancelled tap without movement
- **WHEN** the user taps a collection card and the browser cancels the touch after at most 10 px of movement and at most 2 px of page scroll
- **THEN** the collection is activated as if the tap had completed

#### Scenario: Cancelled touch that moved
- **WHEN** the browser cancels a touch on a card after it travelled more than 10 px or the page scrolled more than 2 px
- **THEN** nothing is activated
