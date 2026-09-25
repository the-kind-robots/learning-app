## Why

A screen appeared when its data read landed, so the last read to land picked
the screen — a read for a screen already left could bring it back over the one
the address names (#486). The address alone now picks the screen.

## What Changes

- Every screen appears the moment it is opened, empty or in its loading state,
  with nothing of its previous visit; its read only fills it.
- A read that lands after its screen was left changes nothing visible.
- What a sync pull re-reads follows the screen on display.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `app-navigation`: new requirement "A screen left before it loads stays
  left" — when a screen appears and what it shows before its read lands.

## Impact

- `src/client/main.cljs` (router picks the screen), `src/client/application.cljs`
  (routes, reload by route), each page's actions and the words and lesson views.
