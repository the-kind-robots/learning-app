## Context

Lesson traffic in the PWA runs through the client/service-worker route layer, not a separate server-side lesson session. The current lesson document is stored locally in `device-db` with a fixed `_id`, and lesson entry currently reuses that stored document via `ensure!`. That makes lesson entry behave like implicit resume, which matches the reported “same session every time” symptom on production.

Word progress is derived from review documents in `user-db`. The current `check-answer!` path already attempts to write review documents for word trials, so this bug should be addressed by making lesson entry deterministic and by adding regression coverage around review persistence and observed progress.

## Goals / Non-Goals

**Goals:**
- Make lesson entry from the UI start a fresh lesson session every time.
- Preserve the existing in-lesson answer and next-step flow.
- Confirm through tests that word-trial answers continue to create review data and influence progress.

**Non-Goals:**
- Redesign the broader offline sync architecture.
- Introduce multi-session lesson history.
- Change example-trial review semantics.

## Decisions

### Start a fresh lesson at route entry
Use the lesson entry route as the boundary for reset semantics. On `GET /lesson`, remove any existing lesson document before creating a new one.

Why this approach:
- It directly matches the intended UX: tapping the lesson entry point starts a new session.
- It keeps lesson progression logic unchanged after the lesson starts.
- It avoids adding age-based or heuristic cleanup rules for stale local sessions.

Alternative considered:
- Keep `ensure!` and try to detect “broken” or “old” lessons. Rejected because it keeps ambiguous resume semantics and leaves stale-session behavior hard to reason about.

### Keep review persistence in the answer path
Retain the current model where word-trial answers create review documents during `check-answer!`.

Why this approach:
- It already matches the retention model used by vocabulary ordering.
- The bug report can be covered by proving this path still runs and is visible after a fresh lesson restart.

Alternative considered:
- Move review persistence to lesson completion. Rejected because it delays progress updates and changes existing semantics.

### Cover the bug with route-level and app-level tests
Add tests that exercise repeated lesson entry and verify that review data exists after word-trial answers.

Why this approach:
- The stale-session symptom appears at the route boundary, not just in pure domain logic.
- The progress complaint needs evidence at the persistence boundary, not only in UI rendering.

## Risks / Trade-offs

- Re-entering lesson will discard an unfinished local lesson session by design. -> This is intentional and matches the selected UX.
- If progress still appears stale after a fresh lesson reset, there may be a second bug in review persistence or retention reads. -> Cover `check-answer!` and post-answer state with regression tests before declaring the fix complete.
- Existing tests may assume `ensure!`-style resume semantics at route entry. -> Update route-facing expectations while leaving low-level helpers intact where appropriate.
