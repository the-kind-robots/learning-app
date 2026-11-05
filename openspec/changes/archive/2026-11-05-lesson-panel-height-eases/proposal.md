## Why

Swiping through a lesson's past trials (#734), the bottom panel takes each trial's height at once: it jumps whenever one answer is taller than the next (#747).

## Changes

- When the trial shown changes, the panel's edge travels to the new height; the text fades where it stands and the new text appears in its place; the button stays.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: how the bottom panel changes while looking back at past trials — [delta](specs/lesson/spec.md).

## Decisions

- The text stays in place, over the text riding the edge — owner, #747, after comparing both on a design canvas (https://claude.ai/artifact/4W26Y1v9RYajG7PmTYK6rZ).
- The layout of #734 stays: the current panel holds its place, the past strip lies over it.
- Motion inside the screen is CSS (ADR-0024). The colour behind the panels is the panel's own `::before`, as tall as the panel shown; the lesson's CSS moves its height and colour by transitions, fades the current panel's parts but its button on `inert`, and lets the strip's answer, one per page, appear by `@starting-style` and leave by Replicant's unmounting attributes. One element paints the colour, so no frame shows a panel without it.
- At the end of a lesson «ЗАКОНЧИТЬ» is one button for the whole panel, placed from the same `--page-panel-height` on the line of the verdict and moved by the same transition, so it travels with the edge instead of fading with the text — owner, #747, after seeing it fade.
- `<page-panel>` only measures: it keeps the height of its last child that is not inert in `--page-panel-height` and carries `easing` while its `page` changes, under which the CSS transitions run. It knows no class of the lesson.
- A first build in script (a scaled layer, copies of the text, the Web Animations API) was dropped: it flickered white on the owner's device and bound the element to the lesson's classes.
- Edge 280ms `cubic-bezier(0.2, 0, 0, 1)`, colour 168ms; text out over the first 30%, in from 45%. With reduced motion asked for, nothing moves.
