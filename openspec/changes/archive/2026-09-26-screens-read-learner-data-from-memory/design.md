## Context

Screens read PouchDB on entry through map/reduce views and `find`, then switch (#494). The numbers and the path are in the issue and its design comment; the behaviour this change must produce is in `specs/learner-data-memory/spec.md` and the deltas beside it. In force and relevant: ADR-0005 (system components), ADR-0006 (user-db replicates, device-db does not), ADR-0007 (sync triggers), ADR-0015 (history is home and one screen).

## Goals / Non-Goals

**Goals:** memory as a projection of user-db and device-db; synchronous screen entry; one-frame transitions including close; deferral of work the screen does not show.

**Non-Goals:** how replicated documents arrive (#499 may replace trigger passes; the feed does not care); the dictionary; lesson grading; changing the history shape of ADR-0015.

## Decisions

**Engine: read, follow, and say what was written.** `db.pouch` gains three plain calls: `update-seq` (where a database's feed stands), `read-docs` (every document, or an id range, a page of 1000 per transaction with a task between pages) and `follow-changes` (the live feed from a sequence, one call per batch). Its writes (`insert`, `bulk-docs`, `remove`) tell a per-`dbs` listener what they wrote, with the new `_rev`, after PouchDB resolves and before the caller's `await` continues. The engine interprets no type. *Alternative:* map/reduce views per type at start — slower (the reviews view alone is 0.8 s) and still leaves the feed to write. *Alternative:* the feed alone for own writes — a caller that awaits its write and then opens a screen would not see it yet.

**Adapter: our own store on maps, a pair of functions per document type.** ADR-0016 records the choice against DataScript, relic, TinyBase, TanStack DB and the sync engines, and the measurements behind the shape. `adapters.memory` gives each word id a card — the word and its review history (`domain.retention/Reviews`: times in seconds, retained, ids) — at a slot in `:cards` given the first time its word or one of its reviews arrives (a review may come before its word), never reused; `:slot-of` maps id to slot. `:words` keeps the words as a sorted map in list order, each with `:search` normalised once. A lesson walks the cards, or a collection's slots, cached on the identity of `:collections` and `:slot-of`, which an answer changes neither of. Collections and examples are held by id, examples also by word. Each type is added and removed by a pair of functions; a new revision is the held version removed and the new one added. A history is never changed — `with-review`/`without-review` return a new one — so memory stays a value. Nothing derived is stored: `domain.retention/urgency` computes a word's urgency from its columns when a screen reads it. A document whose `_rev` equals the held one is dropped — equality, not "greater", since LWW conflict resolution in `sync` can make a lower-generation branch the winner. A property test (test.check) holds that incremental ingest in any batches equals a rebuild from the final documents.

**State: memory lives in the store under `:learner/memory`, and three effects are its only writers.** `adapters.memory-loader/start!` takes the databases and `dispatch`, records both feeds' sequences, reads the words (one id range) and the collections and dispatches `:effect/memory-loaded-basic`, reads the rest (user-db outside the words, and device-db) and dispatches `:effect/memory-loaded-full`, then follows both feeds; every later batch — this app's writes once PouchDB accepted them, another tab's, a pull's — goes to `:effect/memory-changed`. Each effect is one `swap!` with the same pure function over memory; the first two also set `:learner/readiness` to `:basic` or `:full`. The documents ride in the effect's argument marked `:nexus/skip-interpolation`, so Nexus hands them over without walking them for placeholders. No callback returns a function.

**Screens: memory is read on entry.** Each route's `:start` dispatches the page's entry, which reads memory and the active collection id and saves the page slice in one `:effect/save`. An open screen does not follow memory: a change from elsewhere shows the next time a screen is opened. The learner's own change on a screen, and the two load stages, dispatch `:action/refresh-page`, which computes the screen on display again. No read tokens, no overtaken reads, no late reads (#486).

**Close to home renders home before `history.back()`.** The navigation effect dispatches home's entry first, then steps back; when `popstate` arrives the route's `:start` finds home already on display and only refreshes. ADR-0015's four rules and its consequences stay as they are. *Alternative:* replace the screen entry with home and step back — renders in the task too, but leaves a home entry forward of home, so Back after a Forward would not leave the app.

**Deferral.** `:effect/after-paint` runs its actions after the next frame is painted (`requestAnimationFrame` then a macrotask). Route entries put `sync-pull` there, and the words screen the rest of its first page. The lesson in progress is held in app state only: nothing on the device read the stored copy once the screen took its lesson from app state, so it is not written.

**Startup.** The shell shows the splash — no page, no corner control — while `:learner/readiness` is nil, that is until the words and collections are read; adding a word and switching collections need nothing more. The reviews and examples load behind it: the words screen lists its rows with a neutral retention mark until then, and a lesson opened before them is drawn when they arrive.

## Risks / Trade-offs

- [Boot cost of loading every document] → measured and reported (boot to memory-ready); the screen is not blocked by it.
- [Memory size] → under a megabyte for 1500 words and 7100 reviews; grows linearly with reviews.
- [A render that alone exceeds a frame on a slow phone] → measured per transition; reported with numbers rather than hidden.
- [Two tabs writing the same document] → each tab's feed brings the other's write; equality on `_rev` keeps it idempotent.

## Migration Plan

No data migration. Rollback is a revert: storage is unchanged.

## Open Questions

None open.
