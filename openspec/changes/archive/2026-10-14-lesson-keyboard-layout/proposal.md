## Why

A long example is clipped while the on-screen keyboard is open: the prompt gets less height than its card needs, and the lesson body hides the overflow (#573).

## What Changes

- While the keyboard is open the answer starts at two rows, the prompt is 18px, the instruction line is hidden, and the blank above the prompt is dropped.

## Capabilities

### Modified Capabilities
- `lesson`: layout of the lesson with the keyboard open.

## Impact

- `resources/public/css/blocks/lesson.css`, one mobile browser spec.
