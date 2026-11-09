## ADDED Requirements

### Requirement: The app follows the system colour scheme

The app SHALL show its light scheme when the system prefers light or states no preference, and its
dark scheme when the system prefers dark. The app SHALL offer no switch of its own. A change of the
system preference while the app is open SHALL take effect without a reload.

The dark scheme SHALL use the ground `#150f1c`, primary text `#f3ecf8` and secondary text `#ab9eb8`.
The light scheme SHALL look as it did before the dark scheme existed.

#### Scenario: A phone set to dark opens the app

- **WHEN** the system prefers a dark colour scheme and the app opens
- **THEN** every screen has the ground `#150f1c` and its body text is `#f3ecf8`

#### Scenario: The system switches while the app is open

- **WHEN** the system preference changes from light to dark with a screen open
- **THEN** the screen turns dark without a reload

#### Scenario: No preference stated

- **WHEN** the system states no colour-scheme preference
- **THEN** the app shows its light scheme

### Requirement: Text and controls stay readable in both schemes

In the dark scheme, body and secondary text SHALL have a contrast ratio of at least 4.5:1 against the
surface it stands on, and a control's label SHALL have at least 4.5:1 against the control. No
component SHALL keep a colour of the light scheme that is unreadable on the dark ground.

#### Scenario: A primary button in the dark scheme

- **WHEN** a primary button is shown in the dark scheme
- **THEN** its label has a contrast ratio of at least 4.5:1 against the button

#### Scenario: Answer feedback in the dark scheme

- **WHEN** the lesson marks an answer correct or wrong in the dark scheme
- **THEN** the feedback text has a contrast ratio of at least 4.5:1 against its background

### Requirement: Coloured buttons are deep under a light label in the dark scheme

In the dark scheme the blue, green and red buttons SHALL be filled with `#2970a3`, `#23792e` and
`#ba1c1e` under the label `#f3ecf8`. Where blue, green or red is drawn on the ground rather than under a
label — text, an icon, the memory scale, a tile's accent — it SHALL take the light shade `#abd1ed`,
`#6bc774` or `#f76464`. The lesson's progress bar SHALL be the button green `#23792e`. The answer's panel SHALL be `#142416` for a right answer and
`#290a0a` for a wrong one.

#### Scenario: A wrong answer in the dark scheme

- **WHEN** the lesson marks an answer wrong in the dark scheme
- **THEN** «ДАЛЕЕ» is `#ba1c1e` under a light label, on the panel `#290a0a` whose text is `#f76464`

### Requirement: Today's stars burn as street lamps in the dark scheme

In the dark scheme the star for a lesson SHALL be the amber of a sodium lamp, `#ffa81e`, and the star for
five lessons the white-green of a mercury lamp, `#cdffee`. Each SHALL glow in three layers: the hot
middle, the coloured light and the haze. In the statistics a day's bar SHALL be `#ffa81e`, and a day of
five lessons `#96f0d7`.

#### Scenario: Six lessons today in the dark scheme

- **WHEN** the system prefers dark and six lessons were passed today
- **THEN** the bar shows a white-green star and an amber one, each glowing in three layers

### Requirement: The browser chrome and canvas take the scheme's ground

The page SHALL declare a `theme-color` per scheme — white for light, `#150f1c` for dark — and SHALL
declare that it supports both colour schemes, so the browser paints its own canvas and controls in the
active scheme before the stylesheet applies.

#### Scenario: The address bar in the dark scheme

- **WHEN** the system prefers dark and the page loads in a browser that tints its chrome
- **THEN** the chrome takes `#150f1c`

### Requirement: The splash follows the system colour scheme

The splash SHALL be shown in the scheme the system prefers, from the first paint, without any script.
In the light scheme it SHALL show the title «Sprecha», the ü of nine green-to-yellow dots on white,
pulsing one after another along the letter's stroke and each drifting slightly on its own, with purple
sparkles, and «Загружаем...». In the dark scheme it SHALL show the same title, dots and caption on the
ground `#150f1c`, the dots as fireflies that glow and flicker out of step, with lightened purple
sparkles and stray fireflies in the background that pass behind unseen foliage. No foliage shape
SHALL be visible as a shape.

#### Scenario: Light splash

- **WHEN** the app is opened with the system preferring light
- **THEN** the splash shows the ü of nine dots on white, pulsing along the stroke from the left side,
  over the bottom, up the right side, to the umlaut

#### Scenario: Dark splash

- **WHEN** the app is opened with the system preferring dark
- **THEN** the splash has the ground `#150f1c` and its nine dots flicker as fireflies, not in step

### Requirement: The splash holds still under reduced motion

When the system asks for reduced motion, the splash SHALL show no animation in either scheme. Its dots
SHALL be shown at full strength.

#### Scenario: Reduced motion in the dark scheme

- **WHEN** the system prefers dark and reduced motion
- **THEN** the splash shows its nine dots lit and still, and nothing on it moves
