# Tasks

## 1. Memory and the feed

- [x] 1.1 Memory records each database's feed position and takes a batch with its position in one step
- [x] 1.2 `db.pouch/follow-changes` hands on only changes after its position, with the position
- [x] 1.3 Each database carries a marker in a `_local` document, created when missing
- [x] 1.4 A write catches memory up from its database's change log instead of applying what it reads back
- [x] 1.5 A batch of nothing memory keeps leaves memory as it was; a document memory cannot take removes the version it held

## 2. Snapshot

- [x] 2.1 `adapters.learner.snapshot`: header and transit body, checksum, read, write and delete in the Cache API
- [x] 2.2 The loader notes positions and markers, reads and checks the snapshot and the change at each stored position, restores and catches up a page at a time, or reads everything
- [x] 2.3 The snapshot is written once memory is loaded and when the page goes to the background, unless memory's positions are unchanged
- [x] 2.4 Replication passes wait for the snapshot check, nothing else does; the service worker deletes only its own buckets
- [x] 2.5 A golden test of the snapshot format names `format-version`

## 3. Verify

- [x] 3.1 Node: round trip, restore plus catch-up equals a full read, each drop condition, a lost tail
- [x] 3.2 Browser: start from a snapshot, a snapshot dropped on mismatch, the startup specs
- [x] 3.3 Release build, ~1500 words / ~7000 reviews: repeat start with and without a snapshot, first start
