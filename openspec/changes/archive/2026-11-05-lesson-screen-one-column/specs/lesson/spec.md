## ADDED Requirements

### Requirement: The lesson screen keeps one column
On desktop the lesson screen SHALL lay out its progress bar, its answer field, the content of its bottom panel and its main button in one column of one width, centred; the main button SHALL span that column and stand in the same place whether it reads «ПРОВЕРИТЬ», «ДАЛЕЕ» or «ЕЩЁ УРОК», given panels of the same height. The bottom panel after an answer SHALL be a sheet as wide as the column with its padding, with rounded top corners; on a phone it SHALL span the screen width without rounding. The prompt SHALL be set without a frame, under its instruction, at one size whatever its length, and SHALL break a word too long for its line at a syllable. A tile SHALL never be wider than the answer field. On a 360×690 phone a 15-word example SHALL keep its whole prompt inside the lesson body in every state of the trial.

#### Scenario: The main button keeps its place on desktop
- **WHEN** a learner on desktop answers a word trial wrong, then answers the final trial right
- **THEN** «ПРОВЕРИТЬ», «ДАЛЕЕ» and «ЕЩЁ УРОК» each span the column of the progress bar and the answer field
- **AND** the panel's answer starts at the column's left edge

#### Scenario: The panel on a phone
- **WHEN** a learner on a phone answers a trial
- **THEN** the panel spans the screen width

#### Scenario: A long word in tiles
- **WHEN** a learner on a 360 px wide phone places a 34-letter word's tile in the field
- **THEN** the tile lies within the field

#### Scenario: A long example on a small phone
- **WHEN** a learner on a 360×690 phone shows a 15-word example's tiles, places them all and checks a wrong order
- **THEN** in each of these states the whole prompt is inside the lesson body, above the panel
