## Why

Adding a word can trigger a background `example-fetch` task that currently logs a vague warning like `Invalid example: missing required fields`. The warning is technically true, but it hides the real failure mode:

- the backend may be unavailable for example generation in development
- the backend may return an invalid payload
- the client only discovers the problem late, when trying to save the example locally

This makes debugging noisy and slows down understanding of whether the problem is configuration, backend behavior, or client validation.

## What Changes

- Make example-fetch failures surface clearer error causes instead of collapsing into a late `missing required fields` warning.
- Validate example payloads closer to the backend/client boundary.
- Return clearer backend responses when example generation is unavailable or invalid.
- Preserve retry behavior for background example-fetch tasks while improving observability.

## Capabilities

### New Capabilities
- `example-fetch-error-clarity`: Defines how example-fetch failures are validated and surfaced across backend and client boundaries.

### Modified Capabilities
- `examples`: Tightens the contract for successful example payloads used by background fetch tasks.

## Impact

- Affected code: `src/backend/core.clj`, `src/backend/examples.clj`, `src/client/examples.cljs`, `test/client/examples_test.cljs`.
- Affected behavior: background example generation after adding a word, especially in local development.
- Validation focus: invalid payload handling, missing API key handling, and clearer client/task-level failure signaling.
