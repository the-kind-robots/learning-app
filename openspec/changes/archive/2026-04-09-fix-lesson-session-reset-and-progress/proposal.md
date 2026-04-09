## Why

On production, lesson entry can reuse a stale local lesson session instead of starting a fresh session from the current vocabulary state. Word progress also appears not to update after lesson answers, which undermines retention-based ordering and makes lesson behavior look stuck.

## What Changes

- Start a fresh lesson session when the user enters lesson flow from the UI instead of silently resuming an older local lesson document.
- Preserve in-lesson answer and next-step behavior while making lesson entry deterministic and reset-safe.
- Ensure word-trial answers continue to record review data that affects retention/progress and is visible on subsequent lesson entry.
- Add regression coverage for stale lesson reuse and review-driven progress updates.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `lesson`: entering a lesson from the UI must start a new lesson session, and lesson answers must continue to update review-backed progress consistently.

## Impact

- Affected code: `src/client/application.cljs`, `src/client/lesson.cljs`, `src/client/domain/lesson.cljs`, and lesson-related tests under `test/client/`.
- Affected behavior: lesson entry, persisted local lesson session handling, review persistence for word trials, and progress observed after re-entering a lesson.
- Validation focus: repeated lesson entry on prod-like service-worker flow, word-trial review creation, and regression-free lesson progression.
