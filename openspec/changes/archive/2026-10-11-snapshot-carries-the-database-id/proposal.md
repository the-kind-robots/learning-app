## Why

The snapshot's check of "which database" is called a marker, and the code that asks it is spread over three positional values (#508). Naming it what it is, the database's id, lets the refusal check read as one question.

## What Changes

- user-db's marker is called its id; the snapshot stores the id, and a snapshot that stores no id of the current kind is refused. Each device reads every document once after the deploy.

## Capabilities

### New Capabilities

### Modified Capabilities
- `learner-data-memory`: the snapshot requirement says "id" where it said "marker".

## Impact

- `src/client/db/pouch.cljs`, `src/client/adapters/learner/snapshot.cljs`, `src/client/adapters/learner/loader.cljs`, `test/client/memory_snapshot_test.cljs`.
