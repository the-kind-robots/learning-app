# collections-navigation Specification

## Purpose
Define the home-screen collections grid UI: grid icon, collections page, collection cards, create/rename/delete flows, and active-collection switching.

## Requirements

### Requirement: Home screen shows collections grid icon
The system SHALL display a 2×2 grid icon in the top-right area of the home screen header.

#### Scenario: Grid icon visible
- **WHEN** the home screen is rendered
- **THEN** a collections grid icon button is visible in the top-right corner

### Requirement: Tapping collections icon opens collections page
The system SHALL navigate to the collections page when the collections icon is tapped.

#### Scenario: Navigate to collections page
- **WHEN** the user taps the collections grid icon
- **THEN** the app navigates to `:page/collections`
- **AND** the page renders all existing collections plus the dashed create card

### Requirement: Collections page shows collection cards
The system SHALL render one card per collection. Each card SHALL display the collection name and word count. The All Words card SHALL always be present and listed first.

#### Scenario: Collection card displays name and word count
- **WHEN** the collections page is rendered
- **THEN** each card shows the collection name and the count of words belonging to that collection

#### Scenario: All Words card always listed first
- **WHEN** the collections page is rendered
- **THEN** the All Words card appears first

### Requirement: Collections page has a create-collection card
The system SHALL render a dashed "+" card on the collections page to create a new collection.

#### Scenario: Tapping create card opens name input
- **WHEN** the user taps the dashed "+" card
- **THEN** a name input is shown
- **AND** confirming with a non-blank name creates the collection

### Requirement: Tapping a collection card switches the active collection
The system SHALL switch the active collection and navigate back to the home screen when a collection card is tapped.

#### Scenario: Tap collection card activates it
- **WHEN** the user taps a collection card
- **THEN** that collection becomes the active collection
- **AND** the active collection is persisted to localStorage
- **AND** the app navigates back to `:page/home`

### Requirement: Long-press on named collection card enters editing mode
The system SHALL enter an inline editing state with Rename and Delete affordances when the user long-presses a named collection card. Long-press SHALL have no effect on the All Words card.

#### Scenario: Long-press on named collection enters editing
- **WHEN** the user long-presses a named collection card
- **THEN** that card enters an editing state exposing rename input and a delete control

#### Scenario: Long-press has no effect on All Words
- **WHEN** the user long-presses the All Words card
- **THEN** no editing state is entered

### Requirement: Delete collection confirms before removing
The system SHALL show a confirmation dialog before deleting a collection. The dialog text SHALL be "Удалить набор «X»? Слова останутся в «Все слова»." where X is the collection name.

#### Scenario: Confirm delete removes collection
- **WHEN** the user invokes Delete and confirms the dialog
- **THEN** the collection is deleted
- **AND** all word-collection memberships for that collection are removed
- **AND** the grid refreshes without the deleted collection

#### Scenario: Cancel delete leaves collection intact
- **WHEN** the user invokes Delete but cancels the dialog
- **THEN** the collection is not deleted

### Requirement: Rename collection updates collection name
The system SHALL allow the user to rename a named collection via inline editing.

#### Scenario: Rename updates stored collection name
- **WHEN** the user edits the card name and submits a non-blank value
- **THEN** the collection document is updated with the new name
- **AND** the card reflects the new name
