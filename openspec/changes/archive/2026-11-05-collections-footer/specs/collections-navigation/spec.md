## REMOVED Requirements

### Requirement: A floating button creates a collection
**Reason**: The footer's main button creates a collection instead (#773).
**Migration**: "The footer's button creates a collection".

## ADDED Requirements

### Requirement: The footer's button creates a collection
The system SHALL show along the bottom of the themes screen the footer every screen with a main button has, always on screen, with the main button «ДОБАВИТЬ НАБОР», which opens the name prompt; confirming with a non-blank name creates the collection. No floating button SHALL stand over the tiles. The grid SHALL keep a bottom inset so the footer covers no tile.

#### Scenario: Creating from the footer's button
- **WHEN** the user taps «ДОБАВИТЬ НАБОР» and confirms a non-blank name
- **THEN** the collection is created and appears as a tile

#### Scenario: The last tile clear of the footer
- **WHEN** the user on a phone scrolls the themes screen to its end
- **THEN** every tile and «ДОБАВИТЬ НАБОР» are wholly in view

## MODIFIED Requirements

### Requirement: Tapping collections icon opens collections page
The system SHALL navigate to the collections page when the collections icon is tapped.

#### Scenario: Navigate to the themes screen
- **WHEN** the user taps the collections grid icon
- **THEN** the themes screen is shown
- **AND** it renders all existing collections as tiles and the button «ДОБАВИТЬ НАБОР»
