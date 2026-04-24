## Why

Example fetching is currently opaque and can retry forever, so users cannot tell whether a word has no example, a pending example, or a broken fetch task. Users also have no way to ask for an example or save a good example they already know they want to learn.

## What Changes

- Make example fetch task creation idempotent per word so duplicate fetch actions or multiple tabs do not create duplicate active work.
- Dead-letter repeatedly failing example fetch tasks and retain enough failure detail for debugging and UI state.
- Expose example state on vocabulary rows: no example, fetching, failed, or ready.
- Add user controls to fetch, retry, add, edit, replace, and delete examples without automatic replacement loops.
- Allow user-owned examples to coexist with fetched examples; lessons choose a random usable example for the word.
- Store user-owned examples exactly as entered, without sentence structure, so user examples do not surprise the user.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `examples`: add user-visible example management, user-owned examples, fetch state, and unstructured user-example behavior.
- `task-runner`: add idempotent task creation behavior and retry exhaustion/dead-letter behavior for repeat failures.
- `data-model`: extend example and task document shapes for source, modification timestamps, and failure details.

## Impact

- Affected client modules: vocabulary/example UI routes, `examples`, `tasks`, lesson example rendering, and local DB migrations.
- Affected specs: `specs/examples/spec.md`, `specs/task-runner/spec.md`, and `specs/data-model/spec.md`.
- No auth, telemetry, or personal data changes.
