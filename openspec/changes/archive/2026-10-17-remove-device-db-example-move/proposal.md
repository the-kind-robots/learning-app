## Why

A start moved the examples an earlier build kept in `device-db` to `user-db` and swept the task queue out of it. Every device in use has run both once (#541), so they only cost a start code and tests.

## Changes

- A start no longer moves examples out of `device-db` and no longer tidies it: no example move, no task or index sweep.
- The example backfill no longer waits for the move; it waits for memory and the first replication pass.
- **BREAKING** for a device that never ran the move: its `device-db` examples stay where they are and are not read.

## Capabilities

### Modified Capabilities
- `data-model`: the requirement that examples kept on the device move to `user-db` is removed; the no-index requirement drops the sweep.
- `example-backfill`: the move no longer counts among the things the backfill waits for or is answered by.
- `learner-data-memory`: the scenario of the start writing to `device-db` goes.

## Decisions

- Remove the move and the sweep together — one start path less, over keeping the empty background-migration mechanism.
- No data migration — over a last-chance move, since every device has run it.
