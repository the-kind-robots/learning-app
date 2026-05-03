## Why

Finished or cancelled lesson screens should not remain reachable through browser Back. After a learner exits the lesson flow, Back should return to the page before the lesson entry rather than restoring a completed or abandoned challenge.

## What Changes

- Replace lesson exit navigation history entries instead of pushing a new `/home` entry.
- Apply the behavior to both final lesson completion and explicit lesson cancellation.
- Keep lesson answer and next-trial interactions unchanged inside the lesson flow.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `lesson`: lesson exit history behavior changes for completion and cancellation.

## Impact

- Affects HTMX navigation metadata in lesson/home routes and lesson view controls.
- No data model, dependency, or API shape changes.
