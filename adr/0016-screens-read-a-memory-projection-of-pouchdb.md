# 0016. The learner's data in memory is our own store on CLJS maps

- Status: accepted
- Date: 2026-09-26

## Context

Every screen entry read PouchDB through views and `find`; a lesson entry cost
over a second on 1500 words and 7100 reviews (#494). The owner's budget is a
screen with its data within one frame of the tap, so what screens read has to
be in memory, next to the app state. What that memory must do — stay a
projection of PouchDB, follow its change feed, take this app's writes only
once they are durable, answer each screen within a frame — is stated in
`openspec/specs/learner-data-memory/spec.md`, not here. This records what
holds it.

The requirements, written down before choosing, came to: PouchDB stays the
source; one batch is one new immutable value in the app-state atom; per-word
retention kept as reviews come and go; the word list sorted, scoped and
filtered by a pre-normalised substring at 20 000 words and 200 000 reviews;
adapters own the document types, indexes and queries; no heavy dependency.

Surveyed on 2026-09-25:

- DataScript fits the stack, but at the planned scale it holds 1.2–1.5 M
  datoms: a cold load of seconds on published V8 figures, memory likely over
  100 MB, a datalog join that misses a phone frame, and the derived state —
  per-word retention — hand-written anyway. A spike was started and dropped
  before it was measured.
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

The learner's data is held in a store of our own on ClojureScript maps,
`adapters.learner.memory`, kept in the app store:

- each document type is added and removed by a pair of functions; a new
  revision is the version memory held removed and the version as it now
  stands added; the change feed and this app's writes both reach memory
  through them and nothing else;
- each word id has a card holding the word and its review history, at a
  slot given on first arrival of the word or one of its reviews and never
  reused; a lesson walks the cards; the words are also a sorted map
  in list order for the word list; collections and examples are held by id,
  examples also by word;
- each word's review history is held as columns — times in seconds, parsed
  once, retained or not, and ids — and nothing derived from it is stored: a
  word's urgency and retention level are computed from the history when a
  screen reads them;
- a history is never changed: a review in or out makes a new one, so memory
  is a value throughout and an older memory never sees a later review;
- a property test holds that ingesting any sequence of documents, revisions,
  repeats and deletions, in any batches, equals one rebuild from the final
  documents, that every older memory is unchanged, and that what screens
  read agrees with the retention formula applied to the documents.

Five designs were measured side by side in #507 (release build, 1.5k and
20k words): the earlier projection with a derived word order and stored
retention, computing everything on read, a sorted map with stored
retention, and review columns mutated in place, with or without copying.
Review columns were chosen: a word added costs under 0.2 ms at any size
instead of 4–80 ms of re-sorting, and a lesson draw walks every word's
history in 18–32 ms at 20 000 words and 200 000 reviews, against 12–22 ms
for stored retention. The owner took that trade for less code and no
derived state. In review the owner then chose a new history per review
over copying columns once per batch: no batch state, no mutation, and a
review costs well under a millisecond either way.

Why the history lives on a card and not on the word, and why it is never
changed in place. A lesson on a collection reads the collection's words
from a cache keyed on identity. When the history lived on the word, every
answer made a new word and so a new `:words`, and the cache was rebuilt on
the next lesson: 11–12 ms for a collection of 100 and 66–73 ms for one
holding all 20 000 words, against 0.2–0.3 ms and 14–21 ms with cards, whose
slots an answer does not touch (node, advanced build, 200 000 reviews).
Mutating the review columns in place would also have kept that cache valid
without copying, and loaded fastest, but memory would stop being a value:
an older memory would see later reviews (53 of 300 property-test cases
caught one), identity-keyed caches would serve stale data without a sign,
the render watch would not see a change, the action log and its inspector
would show later reviews in earlier states, and no test could check an
older value. Copying the columns once per batch kept memory a value at 28–33
ms per lesson after an answer. Cards give the stable cache and keep memory a
value.

## Consequences

- A new document type a screen needs is added to the ingest path and its
  indexes, and to the property test's generators.
- How replicated documents arrive — trigger passes or live replication
  (#499) — does not matter to the store.
- Start-up reads every document of both databases once; at 20 000 words that
  read, not the store, is most of the cost (#508).
- Rendering, not the query, is what is left of a transition's cost at the
  measured scale.
