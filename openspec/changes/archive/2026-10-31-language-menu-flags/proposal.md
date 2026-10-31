## Why

The language menu under «Sprecha» shows a heading «Язык» over four rows and a check beside the language in use. The owner wants it lighter: a flag in place of the check, on every row, no heading (#690).

## Changes

- Each language row shows its country's flag at its end, where the check was.
- The check beside the language in use is gone; the row's fill and bold name mark it.
- The heading «Язык» is gone from the screen; the group keeps it as its accessible name.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `interface-language`: the menu's rows carry flags, no check and no visible heading.

## Decisions

- English shows the United Kingdom's flag — over the United States', for a learner of German in Europe. A one-line swap if the owner prefers the other.
- The flags are inline SVG in the view, hidden from screen readers: the name already says the language.
- The flags are round, flat badges in the palette's colours (macaw, cardinal, snow), not the real shades — the owner's call, over realistic or pastel flags drawn on the design canvas.
