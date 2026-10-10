## Why

Examples a device is missing are fetched through a task queue stored in `device-db`: one task document per pair, an index over them, a replay on every replication pass. Its writes and index updates cost the main thread (#518), a throttled or failing endpoint keeps every task alive and retried for the life of the device, and every open tab runs its own copy of the queue. Since #528 examples replicate, so what a device is missing is already in memory; the stored queue only restates it (#523).

## What Changes

- **BREAKING** (internal): the stored task queue is removed — the task documents, their index, the runner, the per-pass replay and the dead letters. Leftover task documents and the indexes earlier builds kept in `device-db` are deleted at start, by the tidying that already moves `device-db` examples to `user-db`.
- A fetcher loop asks for the pairs of entry and collection that have no example, one request at a time, holding what it is doing in memory only. It waits for the load, the tidying and the session's first replication pass.
- A tab fetches only while it is visible, and only for a device with an account.
- Requests are paced and time out; each received example is saved at once.
- Each kind of failure has one fixed effect, and nothing retries forever (#518).
- Adding a word writes no task; it wakes the fetcher, which finds the pair missing like any other.

What each of these must do is stated in the delta specs, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `example-backfill`: rewritten — which pairs are asked for and when, readiness, visible tabs, pacing, the failure table, saving at once; the queue, delay, cancel and dead-letter requirements go.
- `task-runner`: removed.
- `data-model`: task and dead-letter shapes go; no index in `device-db`; the start-time tidying also deletes leftover tasks and indexes.
- `examples`: creating an entry no longer creates a task; the session-cookie requirement is about requests, not tasks.
- `examples-schema`: the request carries the collection's name; no task payload.
- `user-phrases`: adding a phrase queues nothing; its example is fetched like a word's.
- `example-fetch-error-clarity`: every answer falls into one failure kind.
- `learner-data-memory`: no task writes and no per-pass backfill.
- `client-runtime-dependencies`, `main-thread-runtime`: the example fetcher component replaces the task runner.

`push-sync` names neither tasks nor a pass listener and is not changed.

## Impact

- Removed: `tasks`, `ports.task-queue`, the task machinery of `adapters.example-fetch`, `adapters.learner` and `use-cases.examples`, the pass listeners of `sync`, `db.pouch/id-with-prefix?` and `ensure-index!`, the `task-data-payload` migration.
- New: `domain.examples`, `ports.examples`, a fetcher loop in `use-cases.examples`, `:learner/save-examples!`, `:sync/first-pass`, the `:examples/fetcher` component in `main`.
- Backend: none. Deployed together with the server PR under #523.
- ADR-0021 records the decision.
