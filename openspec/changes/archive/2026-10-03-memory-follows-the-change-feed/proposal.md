## Why

At 20k words a write, the lesson pick and startup missed their budgets (#508). Every write queried a PouchDB index or view for what it needed, and the first such query after a bulk import built its index over every document. Memory (#494) already holds the learner's data; the database's change log already carries every change in order.

## What Changes

- Memory is loaded in one go behind the splash, with what was stored during the load, then follows each database's change feed. It catches up by one read when the page becomes visible again and before a decision that needs every collection.
- A write reads the document it changes from PouchDB by id — the winner — puts its change over it, once more on a conflict, then reads what it wrote back into memory. It does not wait for the feed.
- Deleting a word removes it from its collections in the same write and keeps its reviews and examples; a failed delete is reported and leaves the word listed. A word added again comes back with its history. Deleting a collection deletes the collection document only.
- After a synchronisation pass the backfill reads the pulled documents by id and leaves out what the pass deleted.
- The splash says when the learner's data cannot be read.
- user-db carries one secondary index, on `type`; the `type` + `word_id` index and the `reviews-by-word` view are no longer required.

## Capabilities

### Modified Capabilities
- `learner-data-memory`: loading, following and catching up; how a write finds and reports its change; the splash; deleting a word.
- `collections-data-model`: deleting a collection deletes the collection only.
- `user-phrases`: the initial review of a phrase follows the rule for a word added again.
- `data-model`: user-db's secondary indexes; the reviews view requirement is removed.

## Impact

- `adapters.learner`, `adapters.learner.loader`, `adapters.learner.memory`, `adapters.learner.documents`, `adapters.example-fetch`, `ports.learner`, `db.pouch`, `tasks`, `use-cases.vocabulary`, `use-cases.collections`, `use-cases.examples`, `application`, `main`. ADR-0017.
