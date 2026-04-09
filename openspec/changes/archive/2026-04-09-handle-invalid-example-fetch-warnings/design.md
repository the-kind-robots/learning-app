## Context

After a new word is created, the client schedules an `example-fetch` background task. That task calls `/api/examples`, receives JSON, and then persists an `example` document locally. Right now the client validates only at save time, so malformed or incomplete backend responses produce a generic warning from `save-example!`.

In development this is especially confusing because failures may come from:

- missing `OPENAI_API_KEY`
- invalid/malformed payloads from the backend example generator
- transport/server errors

The task runner itself is working as designed: it logs the warning and retries. The main issue is that the failure is classified too late and explained too poorly.

## Goals / Non-Goals

**Goals:**
- Fail earlier and more explicitly when `/api/examples` returns an invalid payload.
- Return clearer backend status/error responses for unavailable example generation.
- Keep the current background-task retry model intact.

**Non-Goals:**
- Redesigning example generation prompts.
- Removing retries from `example-fetch`.
- Building a full structured error taxonomy across the whole app.

## Decisions

### 1) Validate example payloads at the API boundary
- Treat a successful example response as requiring at least `value` and `translation`.
- Reject malformed success payloads in `fetch-one` before `save-example!` runs.

### 2) Make backend failures explicit
- If example generation is unavailable in development because `OPENAI_API_KEY` is missing, return a non-200 response with a clear error message.
- If backend generation returns an invalid example payload, return a non-200 response instead of `200` with a malformed body.

### 3) Keep local persistence validation as a final guard
- `save-example!` should still defend against invalid data, but it should no longer be the primary place where backend contract failures are first discovered.

### 4) Preserve task retry semantics
- `example-fetch` should still return `false` on fetch/validation failures so retries continue to work.
- The key change is clearer failure messages and cleaner separation between server-side and client-side validation failures.

## Risks / Trade-offs

- **Risk:** Returning non-200 for missing API key may cause more visible retries in development.  
  **Mitigation:** That is acceptable because the message becomes actionable instead of misleading.

- **Risk:** Some existing code may rely on permissive success parsing.  
  **Mitigation:** Add tests for valid and invalid payloads at the client task boundary.

## Migration Plan

1. Tighten backend availability/payload checks for `/api/examples`.
2. Tighten client parsing in `fetch-one`.
3. Add tests for invalid payload rejection and task retry behavior.
4. Verify logs now point to the real failure cause instead of late save-time validation.

Rollback: revert backend response handling and client payload validation together.

## Open Questions

- Should missing `OPENAI_API_KEY` eventually disable example-fetch task creation in dev, or is clearer failure reporting enough for now?
