## Why

The current home and lesson UI still has several visible motion and layout glitches across desktop and mobile. These glitches make the app feel unstable: elements animate or jump at the wrong times, mobile overlays can cover the active input, and list editing transitions feel broken.

## What Changes

- Fix home-screen footer animation so it runs only when it should, instead of on every return to the home screen.
- Fix mobile add-word help/layout behavior so guidance does not cover the active input.
- Stabilize word-list editing and deletion flows on the home screen, especially around the empty-state transition after removing the last word.
- Stabilize lesson-screen layout so prompt and answer-check UI do not jump when checking answers.
- Audit nearby UI transitions in these flows and fix closely related glitches discovered during verification.

## Capabilities

### New Capabilities
- `home-ui`: Covers home-screen motion, add-word layout, word-list editing, and empty-state transitions.

### Modified Capabilities
- `lesson`: Tightens lesson-screen UI stability requirements during answer checking and result rendering.

## Impact

- Affected code: home screen presenters/views/styles, lesson presenters/views/styles, mobile layout behavior, list editing UI, and transition/animation logic.
- Affected surfaces: home screen, word list, add-word flow, lesson prompt/result layout.
- Validation focus: desktop and mobile home flows, list edit/delete behavior, and lesson answer-check rendering stability.
