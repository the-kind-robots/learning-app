## ADDED Requirements

### Requirement: Checked example keeps the placed tiles

Once an example answered with tiles is checked, right or wrong, the lesson SHALL keep showing the field with the placed tiles, in tray order, above the result's panel and outside its colour. The bank SHALL be gone and the tiles SHALL NOT be movable. With no tile placed the field SHALL be shown empty. While a past trial is shown, the field SHALL be hidden in its place, as the answer field is.

#### Scenario: Wrong example checked

- **WHEN** the learner places tiles in a wrong order and checks
- **THEN** the field shows the placed tiles in the order they were placed
- **AND** below it the result shows «Правильно:» and the correct answer, with no «Ваш ответ:» line

#### Scenario: Right example checked

- **WHEN** the learner places every tile in order and checks
- **THEN** the field shows the placed tiles above «Правильно!»

#### Scenario: Long example checked on a small phone

- **WHEN** a learner on a 360×690 screen checks a fifteen-word example placed in a wrong order
- **THEN** the prompt, the field with the tiles and the result's panel are all on screen, whole

## MODIFIED Requirements

### Requirement: Error reveal labels the correct answer with short copy
The lesson error footer SHALL label the revealed correct answer with the header «Правильно:».

#### Scenario: Wrong answer reveal
- **WHEN** a user answers a word or a phrase incorrectly
- **THEN** the footer shows the user's answer under «Ваш ответ:»
- **AND** the correct answer under «Правильно:»

#### Scenario: Wrong example reveal
- **WHEN** a user answers an example incorrectly
- **THEN** the footer shows the correct answer under «Правильно:» and no «Ваш ответ:», the user's answer being the placed tiles above it
