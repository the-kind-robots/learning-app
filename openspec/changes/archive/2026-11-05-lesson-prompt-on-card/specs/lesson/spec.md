## MODIFIED Requirements

### Requirement: The lesson screen keeps one column
On desktop the lesson screen SHALL lay out its progress bar, its answer field, the content of its bottom panel and its main button in one column of one width, centred; the main button SHALL span that column and stand in the same place whether it reads «ПРОВЕРИТЬ», «ДАЛЕЕ» or «ЕЩЁ УРОК», given panels of the same height. The bottom panel after an answer SHALL be a sheet as wide as the column with its padding, with rounded top corners; on a phone it SHALL span the screen width without rounding. The prompt SHALL be set on a card in a light frame, centred, with its instruction under it, at one size whatever its length and whether or not the on-screen keyboard is shown, and SHALL break a word too long for its line at a syllable. When the on-screen keyboard opens, the answer field, its button and the prompt SHALL move up together, in the same frames, the prompt never after the field; where the browser lets the keyboard overlay the lesson, they SHALL rise in 100 ms. The progress bar SHALL not move. When the answer's panel replaces the field with the keyboard over the lesson, the panel SHALL stand at its place in the frame it appears, and the prompt SHALL ride down to its own. After an answer the main button SHALL take the panel's colour: green after a right answer, red after a wrong one. A tile SHALL never be wider than the answer field. On a 360×690 phone a 15-word example SHALL keep its whole prompt inside the lesson body in every state of the trial.

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

#### Scenario: The keyboard opens over a word trial
- **WHEN** a learner on a phone taps the answer field of a word trial and the on-screen keyboard shortens the screen
- **THEN** the prompt reaches its new place in the frame the answer field reaches its own, and keeps its size
- **AND** its instruction stays visible under it
- **AND** the progress bar stays where it was

#### Scenario: The answer is checked with the keyboard over the lesson
- **WHEN** a learner on a phone types an answer with the keyboard over the lesson and taps «ПРОВЕРИТЬ»
- **THEN** the answer's panel appears at the bottom of the screen, where it stays, as the keyboard slides away over it
- **AND** the prompt rides down to its place

#### Scenario: The main button in the panel's colour
- **WHEN** a learner answers a trial right, then the next one wrong
- **THEN** «ДАЛЕЕ» is green on the green panel, then red on the red one
