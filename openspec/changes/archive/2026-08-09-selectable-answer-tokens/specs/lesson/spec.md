# lesson Delta

## ADDED Requirements

### Requirement: Revealed answer is selectable as continuous text

The revealed correct answer SHALL be selectable with the mouse as one continuous text run, across plain and hinted words alike, so it can be copied whole. Hinted words SHALL remain activatable.

#### Scenario: Selecting across hinted words

- **WHEN** the user drags a selection across the revealed answer
- **THEN** the selection includes the hinted words' text
- **AND** the copied text matches the visible sentence

#### Scenario: Hinted word still opens the hint

- **WHEN** the user clicks a hinted word without dragging
- **THEN** the hint card opens as before
