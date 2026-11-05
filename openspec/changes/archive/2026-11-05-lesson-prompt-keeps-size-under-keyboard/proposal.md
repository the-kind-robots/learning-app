## Why

On a phone the prompt shrinks and its instruction disappears when the on-screen keyboard opens (#763). The keyboard rule set the prompt to 18 px and hid the instruction; before #751 the prompt was 17 px and nobody saw it, at 22 px the screen visibly breaks.

## Changes

- The keyboard rule no longer touches the prompt or its instruction; it still starts the field at two rows.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: the prompt and its instruction stay as they were under the keyboard — [delta](specs/lesson/spec.md).

## Decisions

- Short of room, the prompt and the instruction give way as #751 set: the instruction slides under the header, then the last-resort container query sets the prompt smaller. The keyboard alone is not a reason.
