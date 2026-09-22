> History. The cap this note argues for was removed by `2026-09-21-backfill-without-a-cap`, and
> the collection unfolding it removed was restored by `2026-09-22-one-key-one-task-one-theme`,
> which also replaced the queue read with a task identity. Read those for what holds now.

## Context

The backfill shipped with the change that made examples shared (`2026-09-20-share-examples-across-devices`).
It hangs off the replication engine's after-pass hook, and the hook took no arguments: every call
read the whole vocabulary, every collection, every example for those words and the whole fetch queue,
then queued what was missing. Passes are throttled to one per 30 seconds (ADR-0007) and fire on
local writes, navigation and pokes, so a device that only pushes its own work pays that reading over
and over, and the price rises with the vocabulary.

Two readings are wanted instead of one: the accumulated debt, which is whole-vocabulary work and
belongs to the start, and the debt one pass created, which is bounded by what the pass carried.

## Goals / Non-Goals

**Goals:**

- A replication pass costs work proportional to what it pulled, not to what the device holds.
- A push-only pass costs nothing at all.
- Both ways a pass creates debt are counted: an arriving word, and an arriving collection naming a
  word already here.
- One statement of the rule "does this pair need an example", used by the backfill and by adding a
  word to a collection by hand.

**Non-Goals:**

- Changing which pairs are owed. The lookup rules in `examples-schema` are untouched.
- Draining the whole debt promptly: the cap per pass stays, and what it leaves waits for a later
  pulling pass or the next start.
- Regenerating an example when a word's translation changes (#415).

## Decisions

**The ids come from the pass's own `change` events.** PouchDB attaches the batch it has just written
to each `change` event of a replication (`change.docs`); the `complete` result carries only the
counters — `docs_read`, `docs_written`, `last_seq`. So `db.pouch/sync-once!` listens for `change`,
keeps the ids written in the `pull` direction, and reports them beside the counters as
`:pulled-ids`. The alternative, reading the changes feed after the pass, needs a sequence number
kept across passes and restarts and answers with this device's own writes too, which is a second
source of truth for the same fact.

**The ids travel, not the documents.** A use-case may not name a document type (`"vocab"`,
`"collection"`) — that is what the layering test enforces, and the ids are enough. A pulled id is
looked up in the collections the device holds: an id that names one of them puts every word that
collection names in question. The remaining ids are read through the words view, which answers only
for the ones that are words, so a review or a pairing receipt falls out on its own.

**The backfill takes a scope.** `backfill!` takes `:all` or the ids one pass wrote. `:all` reads the
vocabulary view whole; a scoped pass reads the collections and then the words view by keys. The
collections are read either way: a word's debt cannot be counted without knowing which collections
hold it.

**One backfill at a time.** The start's full pass and every later scoped one run in the order they
were asked for. A backfill queues task documents, and the next one reads them back to know what is
already queued; two running at once would queue the same pair twice and pay the provider twice.

**The rule has one home.** `use-cases.examples/owed?` states it over the examples a device holds for
a word: strict on a named collection, a union on the main card. `owed-requests` applies it per pair,
`owes-example?` reads one word's examples and applies it to a single pair, and
`use-cases.vocabulary` calls that when a duplicate word joins a collection. The adapter's
single-pair `find` is gone with its second copy of the rule.

## Risks / Trade-offs

- **Leftover debt drains more slowly** → what the cap leaves over used to be picked up by the next
  pass of any kind, every 30 seconds. Now it waits for a pass that pulls, or for the next start.
  Accepted: the start closes it, and the cap exists because a burst of provider requests is the
  worse failure.
- **A pass that pulls only reviews still reads the collections** → one small view read, not the
  vocabulary. Accepted; the alternative is teaching the engine what a collection id looks like.
- **The ids are collected in memory for the length of a pass** → a first synchronisation carrying a
  whole vocabulary holds that many strings. Strings only, not documents.

## Migration Plan

None. No stored data changes shape, and a device that starts on the new build runs its full pass
once and continues from there.

## Open Questions

None.
