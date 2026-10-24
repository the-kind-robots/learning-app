## Why

The light bulb is reached from the keyboard only by Shift+Tab and Space, and when it has focus it shows no ring, unlike every other control (#613).

## What Changes

- Alt+Enter in the answer field activates the light bulb, beside Ctrl+Enter that checks the answer.
- The light bulb shows the browser's focus ring when focused from the keyboard.

What each of these must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `lesson`: the light bulb answers the keyboard.

## Decisions

- Alt+Enter, not a letter: independent of the layout, free on the lesson screen, and a pair with Ctrl+Enter. Chosen by the owner.
- The answer field handles the key itself, as a native control handles its own keys; the page knows nothing of it.
- The bulb is a button with an inline SVG, not a checkbox drawn by a mask: the mask clipped the focus ring away, and a checkbox promised an off state the bulb never has. Which of textarea and tiles shows is the element's custom state, `:state(tiles)`.
