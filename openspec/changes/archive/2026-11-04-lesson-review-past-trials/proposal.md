## Why

Once a trial is answered the lesson moves on and the trial is gone: a learner who has answered four of five cannot look back at them without leaving the lesson (#734).

## Changes

- The lesson keeps the trials it has left behind, in order, each with the attempts it took.
- The lesson screen becomes a horizontal strip of trials the learner swipes back through; the bottom panel stays put and shows the past trial's answer.
- The progress bar follows the strip.
- On desktop an arrow beside the progress bar steps back.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: adds the requirement that answered trials can be looked back at — [delta](specs/lesson/spec.md).

## Decisions

- One page per trial answered right, not one per attempt — owner, #734.
- After the last trial the strip still scrolls back, and a past trial's button finishes the lesson, not "Далее" — owner, #734.
- Swiping back while typing keeps the typed text — owner, #734.
- The strip is a custom element, `<page-strip>`: it scrolls to the page the state names and reports the page the learner scrolls to — over effects and placeholders reading the DOM. The panel and the progress bar follow that page; the bar slides by `transform`, which the compositor animates without the stutter `width` had — over a scroll-driven animation that tracks the finger, which is CSS few browsers carry.
- A past trial's hint card shows the word and its translation, without the add button — the dictionary state is fetched only for the trial being answered.
