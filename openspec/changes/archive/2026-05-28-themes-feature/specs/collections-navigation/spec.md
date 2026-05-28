# collections-navigation Specification

## Purpose
Define the home-screen collections grid UI: grid icon, full-screen grid overlay, collection cards, create/rename/delete flows, and active-collection switching.

## ADDED Requirements

### Requirement: Home screen shows collections grid icon and active collection name
The system SHALL display a 2×2 grid icon in the top-right area of the home screen header, with the active collection name displayed as small text beside or below the icon.

#### Scenario: Grid icon and active collection name visible
- **WHEN** the home screen is rendered
- **THEN** a collections grid icon button is visible in the header
- **AND** the current active collection name is shown in small text adjacent to the icon

### Requirement: Tapping collections icon opens collections grid
The system SHALL navigate to the collections grid screen when the collections icon is tapped.

#### Scenario: Navigate to collections grid
- **WHEN** the user taps the collections grid icon
- **THEN** the app navigates to :page/collections
- **AND** the collections grid screen renders all existing collections plus the dashed create card

### Requirement: Collections grid shows collection cards
The system SHALL render one card per collection in a grid layout. Each card SHALL display the collection name and word count. The All Words card SHALL always be present and listed first.

#### Scenario: Collection card displays name and word count
- **WHEN** the collections grid is rendered
- **THEN** each card shows the collection name and the count of words belonging to that collection

#### Scenario: All Words card always listed first
- **WHEN** the collections grid is rendered
- **THEN** the All Words card appears first in the grid

### Requirement: Collections grid has a create-collection card
The system SHALL render a dashed "+" card in the collections grid to create a new collection.

#### Scenario: Tapping create card opens name input
- **WHEN** the user taps the dashed "+" card
- **THEN** a name input dialog or inline input is shown
- **AND** confirming with a non-blank name creates the collection and adds it to the grid

### Requirement: Tapping a collection card switches the active collection
The system SHALL switch the active collection and navigate back to the home screen when a collection card is tapped.

#### Scenario: Tap collection card activates it
- **WHEN** the user taps a collection card in the collections grid
- **THEN** that collection becomes the active collection
- **AND** the active collection is persisted to localStorage
- **AND** the app navigates back to :page/home

### Requirement: Long-press on named collection card shows context menu
The system SHALL show a context menu with Rename and Delete options when the user long-presses a named collection card. Long-press SHALL NOT be available on the All Words card.

#### Scenario: Long-press on named collection shows menu
- **WHEN** the user long-presses a named collection card
- **THEN** a context menu appears with "Rename" and "Delete" options

#### Scenario: Long-press not available on All Words
- **WHEN** the user long-presses the All Words card
- **THEN** no context menu appears

### Requirement: Delete collection shows confirmation dialog
The system SHALL show a confirmation dialog before deleting a collection. The dialog text SHALL be "Удалить набор «X»? Слова останутся в «Все слова»." where X is the collection name.

#### Scenario: Confirm delete removes collection
- **WHEN** the user selects Delete from the context menu and confirms the dialog
- **THEN** the collection is deleted
- **AND** all word-collection memberships for that collection are removed
- **AND** the grid refreshes without the deleted collection

#### Scenario: Cancel delete leaves collection intact
- **WHEN** the user selects Delete but cancels the dialog
- **THEN** the collection is not deleted

### Requirement: Rename collection updates collection name
The system SHALL allow the user to rename a named collection via an input dialog.

#### Scenario: Rename updates stored collection name
- **WHEN** the user selects Rename and submits a non-blank name
- **THEN** the collection document is updated with the new name
- **AND** the grid card reflects the new name
