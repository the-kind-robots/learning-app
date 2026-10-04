## Why

The splash waits for memory, and memory waited for work it does not need (#508). Every start brought each secondary index up to date before the databases were handed over, so the next start after a large pull, or after the task queue rewrote its tasks, spent seconds indexing documents nobody queries that way. The memory read itself waited for the components started before render, and the task queue's first queries ran beside it.

## What Changes

- The app builds no index in user-db. Nothing queries the `type` index; a device that has `_design/by-type` keeps it, and the app neither reads it nor brings it up to date.
- No index is built before memory is loaded. The task queue builds its own index when it starts.
- The memory read starts as soon as the databases are open. Memory is handed to the screens once render exists.
- The task loop begins once memory is loaded, the signal the examples backfill already waits for. A stop before then keeps it from beginning, and a failed index build is logged and keeps it stopped.
- A failed read at start is tried again from its first step, whichever step failed.

## Capabilities

### Modified Capabilities
- `data-model`: which secondary indexes the app builds, and when.
- `task-runner`: when the task loop begins.
- `client-runtime-dependencies`, `main-thread-runtime`: the task runner component starts with the runtime; its loop waits for memory.

## Impact

- `db.pouch`, `tasks`, `ports.task-queue`, `adapters.learner.loader`, `instrumentation`, `main`; the test database fixtures.
