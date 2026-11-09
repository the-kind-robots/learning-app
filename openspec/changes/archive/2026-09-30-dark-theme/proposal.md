## Why

The app has one colour scheme, light (#413). A phone set to dark shows a white screen at night, and
the splash flashes white before the app loads. The palette's meaning is its leaf colours — white, the
green→yellow gradient and purple — so a dark scheme can keep that meaning under a night sky instead of
inverting it.

## What Changes

- The app follows the system colour scheme (`prefers-color-scheme`); there is no manual switch.
- The dark scheme is "night violet": a violet-black ground, near-white text with a violet tint, the
  green→yellow gradient as firefly light, purple lightened.
- Every component draws its colours from the colour tokens; the few hard-coded colours become tokens.
- The browser chrome (`theme-color`) and the page canvas (`color-scheme`) take the scheme's ground.
- The splash follows the scheme: light is the "living scribble" ü, dark is fireflies hiding in
  foliage. Both hold still when reduced motion is asked.
- The manifest's `background_color` stays white: a manifest holds one value, and no shipped browser
  reads a per-scheme one.

## Capabilities

### New Capabilities

- `color-scheme`: the app and its splash follow the system's light or dark scheme, with readable
  contrast in both and a still splash under reduced motion.

### Modified Capabilities

## Impact

`resources/public/css/` (tokens and the blocks that hard-coded colours), the inline splash and the
`<head>` in `src/backend/core.clj`, the retention mark colour in `src/shared/utils.cljc` and its unit
test. No API, data or dependency change.
