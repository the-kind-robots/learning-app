## Why

A synced profile queues hundreds of example fetches (#518). When the examples endpoint answers
`429` with `Retry-After`, the queue reschedules only the task that was refused and keeps sending
the rest, so nearly every queued fetch goes out within seconds and is refused too. After the
Retry-After delay they are all due again, and the loop repeats. The queue never backs off on the
provider's terms, which the example-backfill spec already promises.

## What Changes

- A throttled answer pauses the whole queue, not one task: no new fetch starts until the
  Retry-After delay has passed. The fetches already in flight finish.
- An earlier trigger (a new task, a resume) does not end the pause.
- When the delay has passed, the queue resumes on its own and drains.

## Capabilities

### New Capabilities

### Modified Capabilities

- `example-backfill`: adds the requirement that a throttled answer pauses the example-fetch queue.

## Impact

`src/client/tasks.cljs` (the task runner) and its unit test. No API, data or dependency change.
