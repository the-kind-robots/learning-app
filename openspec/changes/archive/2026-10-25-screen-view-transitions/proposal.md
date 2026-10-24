## Why

On a phone every screen change is a cut: nothing says whether the user went deeper, came back, entered the lesson or moved to the next task. The owner chose the motion in a design canvas (#634); this change carries the screen changes. Motion inside a screen follows in a second change.

## Changes

- Screen changes on a phone run as view transitions: deeper and back slide, the lesson fades through, the next task slides, a deleted collection's neighbours close the gap.
- A desktop-width window, reduced motion, a browser without the API and a back gesture the browser animates itself change screens at once, as today.

## Capabilities

### New Capabilities

- `screen-transitions`: how a screen change moves on a phone.

### Modified Capabilities

None.

## Decisions

- The View Transition API around the one render, over CSS animations of mounted screens — Replicant replaces the screen, so the old one is gone unless the browser snapshots it (ADR-0024).
- Which transition is derived from the state before and after the render, over each action naming its own — the browser's Back reaches the render without any action of ours.
- A phone is a window narrower than 700 px, the width the layout already switches at, over `pointer: coarse` — the owner asked for the phone, and a touch laptop is not one.
- The deletion runs its write inside the transition, over a flag the next render reads — the render that follows the write is synchronous, and an unrelated render could take a flag.
- No animation library: the pseudo-elements take plain keyframes.
