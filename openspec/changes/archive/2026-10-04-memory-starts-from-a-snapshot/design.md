## Context

After #522 and #526 memory is a view the databases keep through their change feeds (ADR-0017), and a start reads every kept document once. The behaviour this change adds is stated in `specs/learner-data-memory/spec.md`; this file says how it is built.

Formats measured on #508 (release build, desktop, 1503 words / 7140 reviews), restore / write: raw documents as JSON ~430 / ~70 ms; the entries memory keeps, as transit, ~180 / ~115 ms, 1.64 MB; the built memory as transit ~100 / ~225 ms. The owner chose the entries.

## Decisions

**What is stored.** The entries memory keeps under `::entries` — `{:kind :entity :rev}` per document id — as a transit vector, after a one-line transit header. A snapshot read from the cache is parsed once into `{:header :body}`; the checks read the header, and only the restore decodes the body. One string, one Cache API entry, one `cache.put`, which the browser writes atomically. Restoring adds each entry to an empty memory with the same function a document's entry goes through, so a change to how memory is built from entries needs no new format. A golden test (`the-snapshot-format-is-the-one-its-version-names`) fails when what an entry holds changes, and names `snapshot/format-version`.

**Positions.** A position is `{:id :rev :seq}`: a change's sequence, and its document's id and revision; every change `db.pouch` reads carries its position. Memory records, per database, the position of the last change that changed it (`memory/positions`); the feed hands each batch with the position of its last change, and memory takes both in one step (`memory/with-changes`). A batch that changes nothing memory keeps returns the same memory, so a task queue's churn neither renders nor rewrites the snapshot; the older position stays valid, because nothing memory keeps was stored between.

**The change at the position.** `db.pouch/holds-position?` reads one change after `seq - 1` (PouchDB 9's idb adapter returns the first change whose document still has its latest change there, in sequence order). When that change is at `seq`, its id and revision must match. When it is later, the change at `seq` was replaced by a later change of its document, and `revsDiff` must report the stored revision as known. A database that lost its tail and stored other documents under the same sequences fails one of the two. `db.pouch/feed-position` reads the newest change (`descending`, `limit 1`), so a full read starts from a position that carries its change too. The start asks `holds-position?` only of a database whose feed has moved past the stored position, both databases at once.

**Changes at or below the position.** `db.pouch/follow-changes` hands on only the changes after the last one it handed over, and does not convert a live change below it. A catch-up and the live feed can otherwise resolve out of order, leaving an older revision in memory with a position past the newer one; a snapshot would then keep that error across starts. Catch-ups asked for while one reads share one more read after it, so a burst of writes does not read the same range again and again.

**A write takes the same path.** A write applies nothing to memory itself. Once PouchDB accepts it, the write awaits `loader/catch-up!` for user-db, where every document of the learner's writes lives (ADR-0019).

**Start order.** `loader/start-reading!` begins the check and the read and returns a promise of the check alone, which resolves with nil. The read itself is kept by the loader until `loader/start!` takes it, so neither the snapshot text nor the first memory is held for the life of the page. `:sync/identity` hands the check's promise to `sync/start!`, which holds this tab's replication passes until it resolves; nothing else waits for it. A pass before the check could bring back the change at the stored position after the database lost others below it, and the check could not tell (ADR-0018). The read starts in a task of its own after the check, so the check resolves before the snapshot is decoded. The catch-up reads the first page of both databases at once, takes user-db's then device-db's, and reads further pages, a task apart, only where a page came back full.

**Marker.** A `_local` document of each database holds a random id. Local documents do not replicate and do not move the feed. A destroyed database (an account switch) comes back without it, gets a new one, and no longer matches a snapshot.

**When it is written.** Once memory is handed over, in a task of its own, and on `visibilitychange` to hidden. Skipped when memory's positions equal those last written or read, and once the loader is stopped. The markers are read once: they do not change while the page lives. Writes are chained, so an older one never lands after a newer one.

**A document memory cannot take.** Memory logs it and removes the version it held, so memory holds what a full read gives. A build that starts to take such a document changes the format version, since an older snapshot lacks it.

**Service worker.** `sw.js` names its buckets with a prefix and deletes only its own other buckets on activation, and the bare-version buckets of earlier builds. The snapshot's cache is the page's, so an update costs no full read.

## Risks

- A write that lands before the check — `check-incoming-auth!` saving an identity or the pairing receipt on a `#key=` link, a migration — is covered by the change at the stored position.
- Another tab's replication is not held by the check. A pull there that brings back the change at the stored position after this device lost local changes below it would pass the check.
