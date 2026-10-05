# 0020. Examples replicate through user-db; memory follows user-db alone

- Status: accepted, supersedes ADR-0017
- Date: 2026-10-05
- Supersedes: ADR-0017 (only its decision that memory reads and follows both databases; its decision that memory follows the change feed stands, as ADR-0019 left it). Also the decision of #450 that examples stay in `device-db`, which no ADR recorded.

## Context

#450 kept examples in `device-db`, which replicates nowhere. Its reasons were these. The server's example cache already saves a second generation across accounts. Replication saves only across one account's own devices. And it costs CouchDB volume that grows with every vocabulary and that the server never reads. The cost left to a second device was taken to be transport only, and #523 was to make that cheap.

In use, a device that receives words by replication queues one fetch per (word, collection) pair, through a per-IP limit of 30 requests a minute that cache hits also spend. 1500 words take about 50 minutes, and lessons start without examples meanwhile. Every device of every account repeats it (#528). A batch transport would shorten the wait; it would not stop each device from asking for what the account already holds.

What a device must do is stated in `openspec/specs/data-model/spec.md`, `examples-schema`, `example-backfill` and `learner-data-memory`. This records why.

## Decision

- Example documents live in `user-db` and replicate.
- An example's id is made of its pair and of its content, and the document holds nothing that depends on the device or the time. A pair may have several examples, and the screens will show them, so the id cannot be the pair alone: that would keep one example per pair and turn every second one into a conflict. An id of random characters would keep the same example twice when two devices fetch it. With the content in the id, and a body PouchDB revisions alike on every device, the same example is one document with one revision everywhere, and different examples are different documents.
- Examples an earlier build kept in `device-db` move to `user-db` after memory is loaded, never in `db-migrations`. A migration there runs before the databases open, so a failure in it blocks the start; the move needs nothing to finish before the app is usable.
- The backfill and the example fetches wait for the move and for the session's first pass; the task queue as a whole does not. Before the first pass, this device does not know what the account already holds, and every fetch it sent then could be one the account did not need.
- With ids made of pair and content, whether a pair is answered is a read of id prefixes in `user-db`, so a fetch asks it without memory. The read exists only because a stored fetch re-checks, when it runs, a decision made when it was queued; it goes when example fetching runs from memory instead of a stored queue (#523, client part).
- Memory follows `user-db` alone. Every type memory keeps now lives there. `device-db` holds the task queue, the identity and migration records, and reading or following it cost a full page through the queue on every first start and a feed of task churn on every start (#518).

Rejected: keeping examples device-local and batching the fetches (#523 alone). Every device still fetches what the account holds.

Rejected: a one-time move behind a marker. When this build takes control of the open pages, `service_worker.cljs` reloads each page an earlier build served. A page that was loaded without a service worker — before one was installed, or by a hard reload — is not reloaded, and keeps running the code it loaded. Such a page of an earlier build can still write an example to `device-db` after this build's first start, and behind a marker that example would never move. The move therefore runs on every start; on a device with nothing to move it is one empty range read.

The move can stop. Any later build may delete it once the remaining cost is acceptable: an example still in `device-db` then stays there unseen, and the backfill fetches its pair again. That costs a request, not the learner's data, since an example is generated, not entered. The move holds nothing else up.

## Consequences

- A freshly paired device has the account's examples after its first pass and sends no example request for them.
- CouchDB holds every example of every account; the server still never reads them.
- The server's example cache keeps its role across accounts.
- Nothing is fetched before the first pass of the session. A device with an account that stays offline fetches nothing, which it could not do anyway.
- The first start of this build reads `user-db` in full once: the snapshot format version changed.
- The task queue (#518) is untouched. Re-scoping it and #523 follows separately.
