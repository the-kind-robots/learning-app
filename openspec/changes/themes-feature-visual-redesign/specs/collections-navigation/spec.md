# collections-navigation Specification

## Purpose
Adapt the collections page to the Safari Tab Grid-inspired design source: 9:16 cards with palette borders, CardPreview previews, per-card editing with immediate delete, drag-reorder, and View Transitions for the home ↔ switcher zoom.

## ADDED Requirements

### Requirement: Collection cards use a 9:16 aspect ratio
The system SHALL lay out each collection card at a 9:16 aspect ratio. Sizing SHALL adapt to viewport width (mobile single column, tablet+ multiple columns).

#### Scenario: Card aspect on mobile and tablet
- **WHEN** the collections page is rendered
- **THEN** each card preserves a 9:16 aspect ratio
- **AND** the column count adapts to the viewport

### Requirement: Card borders cycle through the Duolingo palette
The system SHALL color each named collection card's border using a fixed 8-color palette (green, blue, orange, pink, purple, yellow, red, teal), cycled by the collection's `:order` value. The main "Всё подряд" card uses a neutral border.

#### Scenario: Border color follows order
- **WHEN** the collections page renders a named collection with `:order = N`
- **THEN** its border color is `palette[N mod 8]`

#### Scenario: Main card uses neutral border
- **WHEN** the collections page renders the main card
- **THEN** its border uses the neutral token (no palette color)

### Requirement: Cards render a CardPreview of the collection's first 14 words
The system SHALL render, inside each card, a centered collection name plus the first 14 words of that collection. Each word row SHALL show a small dot whose color reflects the word's current retention level (red / orange / yellow / green).

#### Scenario: Card preview shows words and retention dots
- **WHEN** the collections page is rendered for a non-empty collection
- **THEN** the card shows the collection name and up to 14 word rows
- **AND** each row's dot color matches the retention level of that word

#### Scenario: Empty collection shows skeleton rows
- **WHEN** the collections page is rendered for a collection with no words
- **THEN** the card shows 3 skeleton rows

### Requirement: New-card tile uses a dashed SVG border with a green plus
The system SHALL render the create-collection tile with a dashed SVG outline and a centered green `+` glyph.

#### Scenario: Create tile visual
- **WHEN** the collections page is rendered
- **THEN** the create-collection tile uses a dashed outline (not the palette border) and a green plus sign

### Requirement: Drag-reorder updates the collection order
The system SHALL allow the user to drag a card to reorder collections while in editing state. On drop, the system SHALL persist the new ordering by bulk-updating the affected docs. The main "Всё подряд" card SHALL NOT be draggable and SHALL stay at position 0.

#### Scenario: Drag swaps adjacent orders
- **WHEN** the user drags collection A from position 2 to position 4 in editing state
- **THEN** the document orders of A and the intervening collections are updated to reflect the new sequence
- **AND** the change is persisted on drop

#### Scenario: Main card is locked
- **WHEN** the user tries to drag the main card
- **THEN** the drag does not initiate

### Requirement: Header corner icon morphs between grid and close
The system SHALL render the corner icon as a grid icon on the home screen and as an `×` close icon on the collections page.

#### Scenario: Icon morph on navigation
- **WHEN** the active page is `:page/home`
- **THEN** the corner icon is the grid icon
- **WHEN** the active page is `:page/collections`
- **THEN** the corner icon is the close icon

### Requirement: Home ↔ switcher uses View Transitions where supported
The system SHALL use the View Transitions API to animate the zoom between the home content and the tapped collection card when the browser supports it. Browsers without support SHALL fall back to instant navigation without broken state.

#### Scenario: Supported browser animates the zoom
- **WHEN** the user taps a card on the switcher in a browser that supports View Transitions
- **THEN** the navigation between switcher and home runs through `document.startViewTransition`
- **AND** the tapped card and the home content slot share a `view-transition-name`

#### Scenario: Unsupported browser navigates instantly
- **WHEN** the user taps a card in a browser without View Transitions support
- **THEN** the navigation completes without an animation and the layout is consistent

## REMOVED Requirements

### Requirement: Delete collection shows confirmation dialog
**Reason**: The editing state is now an explicit destructive context — long-press promotes a card into editing mode where the X control deletes immediately. The dialog from the functional release adds friction without protective value (membership cleanup is non-destructive to the words themselves).

**Migration**: actions `confirm-delete-collection` and `cancel-delete-collection` are removed; the dialog `<dialog>` element is removed from the view; delete fires directly from the per-card X.

## MODIFIED Requirements

### Requirement: Long-press on named collection card enters editing mode
The system SHALL enter a per-card editing state when the user long-presses a named collection card. Editing state exposes a rename input, an X delete control, and drag-reorder affordances on the long-pressed card only. Other cards remain in normal state. Tapping outside the editing card exits editing state.

#### Scenario: Long-press promotes one card to editing
- **WHEN** the user long-presses a named card
- **THEN** that card enters editing state
- **AND** other cards remain in normal display state

#### Scenario: Tap outside exits editing
- **WHEN** a card is in editing state
- **AND** the user taps anywhere outside that card
- **THEN** the card exits editing state

#### Scenario: Long-press has no effect on main card
- **WHEN** the user long-presses the main card
- **THEN** no editing state is entered
