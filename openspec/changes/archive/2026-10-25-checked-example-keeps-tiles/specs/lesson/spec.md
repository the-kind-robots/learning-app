## ADDED Requirements

### Requirement: Checked example keeps the placed tiles

Once an example answered with tiles is checked, right or wrong, the lesson SHALL keep showing the field with the placed tiles, in tray order, above the result. The bank SHALL be gone and the tiles SHALL NOT be movable. With no tile placed the field SHALL be shown empty.

#### Scenario: Wrong example checked

- **WHEN** the learner places tiles in a wrong order and checks
- **THEN** the field shows the placed tiles in the order they were placed
- **AND** below it the result shows «Ваш ответ:» above «Правильно:»

#### Scenario: Right example checked

- **WHEN** the learner places every tile in order and checks
- **THEN** the field shows the placed tiles above «Правильно!»
