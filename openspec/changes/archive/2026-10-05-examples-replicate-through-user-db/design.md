## Context

Examples were device-local by #450, and memory followed both databases (ADR-0016, ADR-0017) because examples lived in one and everything else in the other. What the system must do is stated in the delta specs; this file says how it is built. ADR-0020 records the decision.

## Decisions

**The document.** `adapters.learner.documents/example-doc` builds the stored example: its id from `pair-key` (`<word-id>:<collection-id>`, shared with the fetch task's id) and a 12-hex SHA-1 prefix of `[value translation structure]` written as JSON, with every map in the structure sorted by key; the JSON depends on nothing but the content. The body carries no time, its keys come in one order, and the structure is stored sorted. PouchDB computes a new revision as the MD5 of the document's JSON (`deterministic_revs`, on by default), so two devices that write the same example produce the same revision. `save-example!`, the move and the backup import all build the document with `example-doc`, so a moved, a fetched and an imported example cannot differ. A golden test pins one example's id and the revision PouchDB stores for it.

**Saving.** `save-example!` writes through `db.pouch/insert-if-absent`, which treats a conflict as "already there". `lib/db` has the one copy of that rule (`db/insert-if-absent`); `db-migrations` and `adapters.data-export` call it directly, and so does `db.pouch/marker`. `documents/tombstone` keeps only a document's id, revision and type.

**The move.** `adapters.learner/move-device-examples!` reads the `device-db` examples up to `tasks/id-prefix`: an earlier build's generated ids sort before the queue's `task:` ids, so it does not page through the queue. It reads a page of 100 at a time (`db.pouch/read-page`), writes every example of the page with `db.pouch/insert-all-if-absent` — one bulk write whose result already says which ids are held, written or refused as conflicts, so nothing is read back — tombstones the page's copies that are held, and yields with `next-task` before the next page. When it moved anything, it catches memory up at the end. `examples-moved!` waits for memory to load and runs the move through `db.pouch/retried`, the back-off the loader's read uses too. The learner port runs it once, on first demand (`:learner/examples-moved`).

**The gate.** `use-cases.examples/start!` builds the readiness from what sync already exposes: no `:sync/account-id`, ready once the move is done; otherwise also the first call of its pass listener. The `:sync/on-pass` of one sync start calls a listener that subscribes after a pass of that start has completed once at once with `{}`, so the readiness does not depend on subscribing before the first pass. The flag is local to the start (`passed?`), so a sync started again for another account has had no pass yet. It hands the readiness to `adapters.example-fetch/hold-until!` through the learner port, and the start's backfill and every pass's backfill wait for it. The task queue knows nothing of it: it starts once memory is loaded, as before, and only the fetch handler waits. `request-example-if-missing!` waits for neither: an add must not wait on `device-db`, and the fetch it queues is held like any other.

**A fetch already answered.** Content ids make the backfill's rule one read: `documents/example-id-prefix` is the prefix of every example id that `visible-in` sees for a pair, and the fetch handler asks `db.pouch/id-with-prefix?` for it — ids only, no memory, no catch-up. A test checks the prefix against `visible-in`. The read exists only because a stored fetch re-checks, when it runs, a decision made when it was queued; it goes when example fetching runs from memory instead of a stored queue (#523, client part).

**Waiting fetches.** A pass's fetches are due `pass-fetch-delay-ms`: two of the sync engine's push windows (`:sync/push-interval-ms`). The engine publishes the example ids a pass pulled, and `start!` hands them to `adapters.example-fetch/cancel-answered!`, which derives each fetch id from the example id and deletes those queued.

**The pass.** `request-missing-for!` awaits `:learner/catch-up!` and reads everything from memory.

**One example per lesson card.** Until the lesson can show several, `domain.lesson/generate-trials` takes, of a word's visible examples, the one with the smallest id.

**Memory follows user-db alone.** Memory keeps one feed position; the loader follows one feed and `catch-up!` reads one change log. The snapshot header is `{:checksum :marker :position :version}`, and `snapshot/format-version` is 2: a version-1 snapshot holds `device-db` examples and a position per database, and is dropped.

**Account switch.** `sync/forget-account-data!` still deletes the previous account's queued fetches and any `device-db` examples not yet moved, because an incoming key is handled before the move runs.

## Risks

- CouchDB holds every example of every account (#450 weighed this).
- The first start of this build reads user-db in full once: the snapshot version changed.
- Until the move and the first pass are done, no example is fetched. A device with an account that stays offline fetches nothing, which it could not anyway.
- The every-start move reads the few non-task documents of `device-db` with their bodies: a read of ids alone cannot tell an earlier build's example from the identity or a migration record.
