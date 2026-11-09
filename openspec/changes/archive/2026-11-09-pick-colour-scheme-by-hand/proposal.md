## Why

The app follows the phone's colour scheme (#413) and nothing else: a learner who wants it dark on a light
phone, or light on a dark one, cannot have it (#805). And an installed app on Android shows a white strip
under the gesture bar at night, because the manifest paints it white.

## Changes

- The settings under the word mark offer the scheme: «Система», «Светлая», «Тёмная», a row of three
  icons with their names under the languages. The menu widens from 196 to 228 px to hold the names.
- The pick belongs to the device and lives in a cookie, so the server renders the splash, the canvas and
  the browser's bars in it from the first paint.
- The night tokens move from `@media` in `colors.css` to `css/base/night.css`; its `<link>`'s `media`
  says when it applies.
- The manifests declare no `theme_color` or `background_color`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `color-scheme`: the app, the splash and the browser's bars follow the scheme picked on the device,
  else the system's; the settings offer the pick; the installed app's navigation bar follows the phone.

## Decisions

- The pick is the device's, not the learner's synced setting — the owner's call; the language stays
  synced.
- A cookie the server reads, over a client-only store: a page rendered in the system's scheme would show
  it until the script runs.
- One night stylesheet switched by its `media`, over a second copy of the night tokens under
  `[data-scheme=dark]` or a move to `light-dark()` (the tokens are RGB triples read in 264 places).
- No manifest colours, over white ones: Chrome on Android paints an installed app's navigation bar from
  them at install, and reads no per-scheme value.
