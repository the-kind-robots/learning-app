## Context

Lesson entry uses HTMX navigation from home into `/lesson`. Lesson completion and cancellation both delete the local lesson state and render home into `#app`. Today exit navigation pushes `/home`, leaving the lesson URL and prior lesson screen in browser history.

## Goals / Non-Goals

**Goals:**

- Make lesson completion replace the current history entry with `/home`.
- Make explicit lesson cancellation use the same replacement behavior.
- Preserve normal in-lesson answer and next-trial swaps.

**Non-Goals:**

- Do not change lesson persistence, trial selection, or progress semantics.
- Do not add custom browser history JavaScript beyond HTMX metadata.

## Decisions

- Use HTMX replace-url semantics for lesson exit responses. HTMX already supports `HX-Replace-Url`, which matches the desired "current entry becomes home" behavior without custom client code.
- Keep lesson entry as a pushed URL. The user can still land on a lesson URL while studying; only exit removes the completed or cancelled lesson screen from Back history.
- Remove push-url from the cancel control and let the server response own the replacement URL. This keeps completion and cancellation behavior identical.

## Risks / Trade-offs

- Back behavior depends on HTMX history handling in the browser. Mitigation: verify with a browser-level flow, not only markup inspection.
- Replacing only on exit means an unfinished active lesson still has a `/lesson` entry while the learner is inside it. This is intended; the requirement targets finished or cancelled lessons.
