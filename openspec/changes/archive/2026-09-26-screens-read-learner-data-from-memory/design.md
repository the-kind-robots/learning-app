## Context

Screens read PouchDB on entry through map/reduce views and `find`, then switch (#494). The numbers and the path are in the issue and its design comment; the behaviour this change must produce is in `specs/learner-data-memory/spec.md` and the deltas beside it. In force and relevant: ADR-0005 (system components), ADR-0006 (user-db replicates, device-db does not), ADR-0007 (sync triggers), ADR-0015 (history is home and one screen).

## Goals / Non-Goals

**Goals:** memory as a projection of user-db and device-db; synchronous screen entry; one-frame transitions including close; deferral of work the screen does not show.

**Non-Goals:** how replicated documents arrive (#499 may replace trigger passes; the feed does not care); the dictionary; lesson grading; changing the history shape of ADR-0015.

## Decisions

**Engine: follow a database, and say what was written.** `db.pouch` gains `follow!`: record `info().update_seq`, read every document (`allDocs include_docs`), hand them over, then open `changes {live, since: seq, include_docs}` and hand over each batch. Its writes (`insert`, `bulk-docs`, `remove`) tell a per-`dbs` listener what they wrote, with the new `_rev`, after PouchDB resolves and before the caller's `await` continues. The engine interprets no type. *Alternative:* map/reduce views per type at start — slower (the reviews view alone is 0.8 s) and still leaves the feed to write. *Alternative:* the feed alone for own writes — a caller that awaits its write and then opens a screen would not see it yet.

**Adapter: our own store on maps, one ingest path.** ADR-0016 records the choice against DataScript, relic, TinyBase, TanStack DB and the sync engines. `adapters.memory` owns the reduction of documents into the domain shape: words by id (a record, so the per-word reads over a whole vocabulary are field reads) with `:search` normalised once and `:retention` folded from its reviews; reviews by id and by word; examples by id and by word; collections by id; and the words as one vector in list order. `ingest` takes the document as held against the document as it stands and updates the primary indexes; what it touched — words moved, words reviewed — is brought up to date once per batch (a word's retention recomputed once, the list sorted once when a batch moves more than 64 words, otherwise each word replaced or spliced in place). A document whose `_rev` equals the held one is dropped — equality, not "greater", since LWW conflict resolution in `sync` can make a lower-generation branch the winner. A property test (test.check) holds that incremental ingest in any batches equals a rebuild from the final documents.

**State: memory lives in the store under `:learner/memory`.** Written by one `swap!` per feed batch or write, so a pull of many documents renders once. `:learner/ready?` says the load completed. Pages see domain shapes only; the layering test still holds.

**Screens: entry is a synchronous effect.** Each route's `:start` dispatches the page's entry, which reads memory and the active collection id and saves the page slice in one `:effect/save`. A change to memory re-computes the page on display in the same `swap!` (the page's refresh fn keyed by `:page/current`), so another tab's write or a pull reaches the open screen without a reload action. No read tokens, no overtaken reads, no late reads (#486).

**Close to home renders home before `history.back()`.** The navigation effect dispatches home's entry first, then steps back; when `popstate` arrives the route's `:start` finds home already on display and only refreshes. ADR-0015's four rules and its consequences stay as they are. *Alternative:* replace the screen entry with home and step back — renders in the task too, but leaves a home entry forward of home, so Back after a Forward would not leave the app.

**Deferral.** `:effect/after-paint` runs its actions after the next frame is painted (`requestAnimationFrame` then a macrotask). Route entries put `sync-pull` there; the lesson's document write and the `:stop` that ends the lesson go through one serial chain so a quick re-entry cannot remove the lesson it just saved. Answer checking takes the lesson state from app state, not from the lesson document.

**Startup.** A screen opened before memory is ready opens at once without a claim about the data and fills in when `:learner/ready?` turns true, through the same refresh as any memory change.

## Risks / Trade-offs

- [Boot cost of loading every document] → measured and reported (boot to memory-ready); the screen is not blocked by it.
- [Memory size] → under a megabyte for 1500 words and 7100 reviews; grows linearly with reviews.
- [A render that alone exceeds a frame on a slow phone] → measured per transition; reported with numbers rather than hidden.
- [Two tabs writing the same document] → each tab's feed brings the other's write; equality on `_rev` keeps it idempotent.

## Migration Plan

No data migration. Rollback is a revert: storage is unchanged.

## Open Questions

None open.
