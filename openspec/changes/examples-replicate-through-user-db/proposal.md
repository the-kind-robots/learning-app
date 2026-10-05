## Why

Examples live in `device-db`, which replicates nowhere. A device that receives words by replication has no examples for them and queues one fetch per (word, collection) pair through a per-IP limit of 30 requests a minute: about 50 minutes for 1500 words, with lessons starting without examples meanwhile (#528). Every device of every account repeats this. #450 kept examples device-local to save CouchDB volume; the owner reversed it on 2026-10-05, because in practice the second device floods the limit and the batch transport (#523) alone keeps every device fetching what the account already has.

## What Changes

- Examples move to `user-db` and replicate with the account. Their id comes from their pair and their content, so a pair keeps every distinct example and the same example is one document on every device.
- Examples a device kept in `device-db` move to `user-db` after memory loads.
- The backfill and the example fetches wait for that move and for the session's first pass; a fetch asks the backfill's question before it sends a request, and a pass that brings an example cancels the fetch it answers.
- Memory follows `user-db` alone, and the snapshot format version changes.
- A lesson card uses one of a word's examples, the same one every time, until the lesson can show several.

What each of these must do is stated in the delta specs, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data-model`: where examples live, their shape, and the move from `device-db`.
- `examples-schema`: an example's identity.
- `example-backfill`: when the backfill counts, when a pass's fetches are due, and what a fetch asks before it sends a request.
- `learner-data-memory`: memory is a projection of `user-db` alone.
- `lesson`: one example trial per word.

## Impact

- `adapters.learner.documents`, `adapters.example-fetch`, `adapters.learner`, `ports.learner`, `tasks`, `use-cases.examples`, `adapters.learner.memory`, `adapters.learner.loader`, `adapters.learner.snapshot`, `application`, `domain.lesson`, `sync`, `db.pouch`, `db` (`lib/db`), `db-migrations`, `adapters.data-export`, `main`.
- CouchDB: example documents per account. Nothing on the server reads them; no filter or validation refuses them.
- ADR-0020 records the decision.
