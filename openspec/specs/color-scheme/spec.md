# color-scheme Specification

## Purpose
The app and its splash follow the light or dark colour scheme picked on the device, else the system's.

## Requirements

### Requirement: The app follows the scheme picked on the device, else the system's

The app SHALL show the scheme picked on the device, light or dark. With no pick it SHALL show its light
scheme when the system prefers light or states no preference, and its dark scheme when the system prefers
dark. A new pick, and with no pick a change of the system preference, SHALL take effect without a reload
while the app is open. The pick SHALL stay on the device across restarts and SHALL NOT reach the
learner's other devices.

The dark scheme SHALL use the ground `#150f1c`, primary text `#f3ecf8` and secondary text `#ab9eb8`.
The light scheme SHALL look as it did before the dark scheme existed.

#### Scenario: A phone set to dark opens the app

- **WHEN** the system prefers a dark colour scheme, nothing is picked on the device and the app opens
- **THEN** every screen has the ground `#150f1c` and its body text is `#f3ecf8`

#### Scenario: The system switches while the app is open

- **WHEN** nothing is picked on the device and the system preference changes from light to dark with a
  screen open
- **THEN** the screen turns dark without a reload

#### Scenario: No preference stated

- **WHEN** nothing is picked on the device and the system states no colour-scheme preference
- **THEN** the app shows its light scheme

#### Scenario: Dark picked on a light phone

- **WHEN** the system prefers light and dark is picked on the device
- **THEN** every screen has the ground `#150f1c`, and opened again the app is still dark

#### Scenario: Light picked on a dark phone

- **WHEN** the system prefers dark and light is picked on the device
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

The page SHALL declare the `theme-color` of the scheme the app shows — white for light, `#150f1c` for
dark — and the schemes it may show, so the browser paints its own canvas, controls and bars in that
scheme before the stylesheet applies, and follows a new pick without a reload. The web app manifest
SHALL declare no theme or background colour, so the navigation bar of an app installed on Android
follows the system's scheme.

#### Scenario: The address bar in the dark scheme

- **WHEN** the system prefers dark and the page loads in a browser that tints its chrome
- **THEN** the chrome takes `#150f1c`

#### Scenario: The address bar after a dark pick

- **WHEN** the system prefers light and the learner picks dark
- **THEN** the chrome takes `#150f1c` without a reload

#### Scenario: The installed app's gesture bar at night

- **WHEN** the app installed on an Android phone is open and the phone prefers dark
- **THEN** the strip under the gesture bar is dark, not white

### Requirement: The splash follows the scheme the app will show

The splash SHALL be shown in the scheme the app will show — the device's pick, else the one the system
prefers — from the first paint, without any script.
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

#### Scenario: Dark picked, the phone light

- **WHEN** dark is picked on the device and the app is opened with the system preferring light
- **THEN** the splash has the ground `#150f1c` from the first paint

### Requirement: The splash holds still under reduced motion

When the system asks for reduced motion, the splash SHALL show no animation in either scheme. Its dots
SHALL be shown at full strength.

#### Scenario: Reduced motion in the dark scheme

- **WHEN** the system prefers dark and reduced motion
- **THEN** the splash shows its nine dots lit and still, and nothing on it moves

### Requirement: The settings offer the colour scheme

The settings under the word mark SHALL offer the scheme under the languages: «Система», «Светлая» and
«Тёмная» in one row, each an icon with its name under it, the device's pick marked — «Система» when
nothing is picked. A pick SHALL take effect at once, every colour on the screen in step: no control SHALL
ease into the new colours after the rest. On a phone the whole screen SHALL dissolve into the new scheme
in about 250 ms; the browser's bar, which cannot dissolve, SHALL take the new colour halfway through, not
before the screen. «Система» SHALL drop the pick. While the settings ask for the language at the first
start they SHALL NOT offer the scheme.

#### Scenario: Dark picked from the settings

- **WHEN** the system prefers light and the learner opens the settings and picks «Тёмная»
- **THEN** the app turns dark at once, all its colours in step, and «Тёмная» is marked

#### Scenario: Back to the system

- **WHEN** dark is picked and the learner picks «Система» on a light phone
- **THEN** the app turns light and follows the system again

#### Scenario: The first start asks only the language

- **WHEN** the settings ask for the language at the first start
- **THEN** they offer no scheme
