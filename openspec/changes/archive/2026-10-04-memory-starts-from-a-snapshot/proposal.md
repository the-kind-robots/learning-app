## Why

Every start reads every document of both databases before the splash goes (#508). On a desktop release build with 1503 words and 7140 reviews the user-db read alone is about 1.5 s, and it grows with the vocabulary. A repeat start needs only what changed since the last one.

## What Changes

- Memory is kept as a snapshot in the browser's Cache API: what memory took from each document, with each database's feed position — its sequence and the change at it — and a marker of the database it came from.
- A start reads the snapshot, checks it against the databases, and catches up from its feed positions instead of reading every document. Any failed check drops the snapshot, and the start reads everything as it does today.
- Each database carries a marker of its own, so a re-created or cleared database is not taken for the old one.
- The snapshot is written once memory is loaded and when the page goes to the background.
- Memory records the feed position of each database it has taken changes up to, and a change at or below that position no longer reaches memory. A batch of documents memory does not keep leaves memory as it was, and a document memory cannot take removes the version it held.
- A write no longer applies the documents it reads back; once PouchDB accepts it, the write catches memory up from its database's change log, so a feed batch read before the write cannot leave an older revision in memory.
- A new build of the service worker keeps the snapshot.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `learner-data-memory`: how memory is loaded at start (from a snapshot when one passes its checks), when the snapshot is written and dropped, how memory takes changes by feed position, and how a write reaches memory.

## Impact

- `adapters.learner` (writes), `adapters.learner.loader`, `adapters.learner.memory`, a new `adapters.learner.snapshot`, `db.pouch`, `application` (the memory effects), `sync` and `main` (replication waits for the snapshot check; nothing else does), `resources/public/js/sw.js`.
- New client dependency: `com.cognitect/transit-cljs`.
- ADR-0018 records the snapshot decision; ADR-0019 supersedes ADR-0017's decision on how a write reaches memory.
