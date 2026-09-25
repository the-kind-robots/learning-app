# 0016. Screens read a memory projection of PouchDB, held in our own store on CLJS maps

- Status: accepted
- Date: 2026-09-26

## Context

Every screen entry read PouchDB through views and `find`, and a lesson entry
cost over a second on 1500 words and 7100 reviews (#494). The owner's budget is
a screen with its data within one frame of the tap. The learner's data is
small, but it is replicated: it must never exist only in memory, since a tab
can be frozen or reloaded at any moment and whatever it held alone is lost.

What the store has to do, written down before choosing one:

- PouchDB (`user-db` replicated, `device-db` local) stays the source; the
  store holds nothing PouchDB lacks.
- It is loaded at a recorded update sequence and then follows the change
  feed; this app's writes reach it once PouchDB accepted them. A revision it
  already holds is a no-op — by equality, since last-writer-wins conflict
  resolution may leave a lower generation as the winner. A deletion arrives
  as an id alone, and a reference may arrive before what it names. One batch
  is one new state and one render.
- Reads are synchronous and fit a frame at the planned scale of 20 000 words
  and 200 000 reviews: the word list in order, scoped and filtered by a
  pre-normalised substring, with its counts; each word's retention kept as
  reviews come and go; the lesson's most due; examples visible in a
  collection; collection summaries.
- It is an immutable value in the app-state atom, inspectable, rendered
  purely; adapters own the document types, indexes and queries.
- No heavy dependency, and start-up no slower.

Surveyed on 2026-09-25:

- DataScript fits the stack, but at the planned scale it holds 1.2–1.5 M
  datoms: a cold load of seconds on published V8 figures, memory likely over
  100 MB, and a datalog join that misses a phone frame. The derived state —
  per-word retention — would be hand-written anyway. A spike was started and
  dropped before it was measured.
- relic is the one library with incremental materialised views, and it is
  experimental.
- TinyBase and TanStack DB are synchronous and capable, but cost a JS↔CLJS
  conversion on every read and hand out copies rather than persistent values;
  TanStack DB also has an open first-N loading regression (TanStack/db#1894).
- RxDB, Datahike, O'Doyle and LokiJS are asynchronous, need a server hop, are
  slow at this size, or unmaintained.
- Sync engines (Zero, LiveStore, Instant, Electric) mean leaving CouchDB, which
  this size does not justify.

## Decision

The learner's data that screens show is held in app state as a projection of
the local databases, in a store of our own on ClojureScript maps
(`adapters.memory`):

- one ingest path takes a document as memory held it against the document as
  it now stands, either absent, and updates every index from the difference —
  the feed and write-through both go through it and nothing else writes
  memory;
- entity tables by id, reviews and examples by word, and a sorted vector of
  the words in list order; each word carries the retention state of its
  reviews, recomputed once per batch for the words the batch touched;
- screens compute what they show from memory, synchronously, in the task of
  the tap;
- a property test holds that ingesting any sequence of documents, revisions,
  repeats and deletions, in any batches, gives the memory a single batch of
  the final documents gives.

A plain sorted vector is enough for the word order: moving one word into
place measured about a millisecond at 20 000 words, so `data.avl` was not
added.

## Consequences

- Screen entry and the words filter cost no storage read; their cost is a
  computation over memory.
- Nothing written by the app is visible before it is durable, and nothing in
  memory is lost with it: a reload rebuilds it.
- Start-up reads every document of both databases once. A screen opened
  before that completes shows no claim about the data and fills in after.
- A new document type a screen needs is added to the ingest path and its
  indexes, not read on entry; the property test covers it once its generator
  is added.
- How replicated documents arrive — trigger passes or live replication
  (#499) — does not matter to the store.
- Rendering, not the query, is what is left of a transition's cost: the
  word list's first page of 50 rows is the heaviest render.
