## Context

Behaviour is in the delta specs. Design: the approved mockup «J» (Nunito, the app's colours, pressed buttons).

## Decisions

- An example is written where it is read: a text area lies over the same text set as a paragraph, so the bold word stays and writing moves nothing. This replaces the write mode and its buttons.
- The dialog keeps what it opened with next to what is typed, so reset and the changed-check need no copy of the opened data kept apart.
- Which buttons are on, and whether the edit can be saved, are worked out from the facts when the screen is drawn, not stored.
- The colour of a collection is the one its folder's tile has on the themes screen, so both screens read one rule; the stylesheet names the colours.
