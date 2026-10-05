# 0018. A snapshot of memory starts the app; it is a guarded cache, never migrated

- Status: accepted
- Date: 2026-10-04

## Context

Memory (ADR-0016) is a view the databases keep through their change feeds (ADR-0017). A start read every kept document of both databases behind the splash: about 1.5 s for user-db at 1503 words and 7140 reviews on a desktop release build, growing with the vocabulary (#508). What a start from a snapshot must do, and when the snapshot is used, written and dropped, is stated in `openspec/specs/learner-data-memory/spec.md`, requirement "A repeat start reads memory from a snapshot". This records the choices behind it.

Three formats were measured (restore / write): raw documents as JSON ~430 / ~70 ms; the entries memory keeps per document, as transit, ~180 / ~115 ms at 1.64 MB; the built memory as transit ~100 / ~225 ms.

## Decision

- What is stored is the entries memory keeps per document, as transit, in one Cache API entry (`adapters.learner.snapshot`). Not raw documents: those pay the conversion on every start. Not the built memory: its shape changes with every optimisation of memory, and it is slower to write.
- A database is identified by a marker of its own in a `_local` document, and a feed position is identified by its sequence together with the id and revision of the change at it. A sequence alone cannot tell a database that lost its last writes and stored others under the same sequences from the one the snapshot was taken from; the change at the sequence can. PouchDB's own `db.id()` was not used: its contract is not documented.
- Only this tab's replication waits for the check (`sync/start!` holds its passes until it resolves); screens, routing and the rest of the start do not. The change at the position cannot tell a database that lost several changes and then got its last one back from a pull: the pull restores the change the check looks at, not the local documents lost below it. Holding the passes keeps such a pull after the check, which then sees the database behind the stored position. Another tab's replication is not held; that case stays open.
- No migrations. The format version is the one lever, and a golden test of the format names it when what memory takes from a document changes.
- Memory carries its own feed positions, so that the memory written and its positions come from one value.

## Consequences

- A repeat start costs the snapshot's restore and a catch-up, not a read of every document; a first start, a new format version or a failed check costs one full read as before.
- A sudden shutdown leaves the previous snapshot, and the catch-up covers what came after it; a torn write fails the checksum; a database that lost writes the cache kept fails the position check.
- The service worker keeps the snapshot's cache when a new build activates.
- Moving PouchDB into a worker (#508) is decided after this, on what main-thread work remains.
