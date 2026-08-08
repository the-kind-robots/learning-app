# Proposal: lesson-token-popover

## Why

GH-257 restored the token-info card as a native `<dialog>` (showModal): it opens centered with UA dialog chrome and a focus ring on the action button. The pre-Replicant UX was a popover anchored above the clicked word with an arrow, light-dismiss, and auto-close timers (`popover.js`, dropped in GH-151). GitHub issue: #273.

## What Changes

- Token-info card renders as an anchored popover above the clicked answer token (HTML Popover API), replacing the centered modal dialog.
- Auto-close behavior restored: 900 ms for a word already in the dictionary, idle timeout on hover-capable devices, pointer/focus inside the card cancels the timer.
- Add action shown only when the word or the translation is missing from the vocabulary (unchanged logic, restated).
- `popover.js` positioning/auto-close logic ported into a ClojureScript namespace driven by lesson effects; no separate static JS file.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `lesson`: token-info card SHALL be an anchored popover with light-dismiss and auto-close, not a centered modal.

## Impact

- `src/client/pages/lesson/view.cljs` — popover shell instead of `<dialog>`; aria-expanded on tokens.
- `src/client/pages/lesson/effects.cljs` + new popover namespace — show/position/auto-close wiring.
- `resources/public/css/blocks/popover.css`, `token-card.css` — already present, reused.
