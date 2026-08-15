## Context

Users want to learn multi-word German expressions as units. Today the client only supports single dictionary words: the home form is a pair of single-line inputs with lemma autocomplete, vocab docs are `vocab:<normalized>` (ADR-0008), lessons type-check RU→DE answers against `normalize-german` equality, and every added word queues LLM example generation. The owner's decisions: a phrase is an exceptional first-class entity ("a word and an example in one") with its own SRS log; lesson answers must be typed fully correctly (no self-check); multi-line input is required; the dictionary's 5206 pos=phrase lemmas must be accounted for.

In-force ADRs constraining this design: 0008 (content-addressed ids, frozen `normalize-german`, conflict resolution unions translations), 0005 (client runtime system/component graph), 0007/0009 (sync triggers — untouched). None is superseded by this change; 0008 is extended, not revisited.

## Goals / Non-Goals

**Goals:**
- Phrase document type with its own review log, replicated and conflict-resolved like vocab.
- Frictionless authoring: automatic word/phrase mode detection, auto-growing textareas.
- Phrase lesson trial with exact typing, reusing the existing error footer.
- Dictionary pos=phrase suggestions flow into the new entity.

**Non-Goals:**
- No LLM translation or example generation for phrases.
- No mass import of dictionary phrases.
- No migration of existing multi-word vocab docs (they stay words; a phrase with the same value is a separate entity).
- No changes to dictionary pipeline, SQLite artifact, backend API, or CouchDB schema.
- No change to word-trial review discipline.

## Decisions

1. **A phrase is a vocabulary document with `kind: "phrase"`** (ADR-0011). The id stays `vocab:<normalize-german(value)>`, so cross-device dedup is free and spaces stay in the id. The alternative — a `phrase:` namespace of its own — was built first and rejected on review: it made the kind part of the identity, so a mis-detected kind could never be corrected, and the only thing it bought (one text held as both a word and a phrase) has no use anyone could name — four values in a 158k-lemma dictionary, all interjections. `phrase:<uuid>` with editable text stays rejected: vocab already fixed value-immutability (GH-96).
2. **Translation is a single entry, never split.** `parse-translations` (splits on `[,;.]`) is bypassed on add, conflict merge, and edit — a sentence translation must survive punctuation. Conflict resolution unions translation entries (ADR-0008 semantics) without splitting values.
3. **Nothing to register:** storage routing, replication, export, collections, progress-store queries and the conflict scan already speak `vocab`, and a phrase is one. Uniqueness cannot live anywhere else anyway — `_id` is CouchDB's only unique key, Mango indexes are secondary and non-unique, and dedup between devices works precisely because both compute the same `_id`.
4. **Separate use-case `phrase-add!`** rather than flags inside `vocabulary/add!`: dedup by value, single-entry translation merge that leaves an existing document's kind alone, initial review seed, collection membership, and no `examples/request!` (a phrase is its own example; also avoids backend `same-lemma?` validation burning LLM retries on multi-word lemmas).
5. **Automatic mode detection, no manual toggle.** Space in trimmed input → phrase, except article+word (der/die/das/ein/eine), `sich`+word, or input matching a non-phrase dictionary lemma from already-fetched completions; picking a suggestion decides by its pos (multi-word pos=phrase → phrase). Tappable chip appears only when phrase is detected, as override until the form clears. Both fields are always auto-growing textareas (rows=1 compact) so a mode flip never remounts inputs or loses text. Mode flip must clear open suggestions and guard stale async completion responses. Alternative — explicit segment toggle — rejected by owner as tiresome.
6. **Grading normalization is a new function, `normalize-for-grading`** in shared utils — `normalize-german` is a frozen id contract (ADR-0008) and must not change. Forgiveness is typography-only: apostrophes (U+0027/U+2019/U+02BC) deleted, unicode quotes/dashes → space. Words are never forgiven.
7. **No markup of where the answer differs.** The error footer shows the typed answer and the reference as plain text. A token diff was built and then dropped from this change: it has to serve word and example trials too, and those grade through `normalize-german` while the diff would normalize through `normalize-for-grading` — a token could read as matched while the grader called it wrong. Designed from scratch in its own change.
8. **Review discipline for phrase trials: one review per trial-id per lesson**, tracked as `:reviewed-trial-ids` in lesson state — not a flag on the trial (trials are compared by value in `remove-trial`). Prevents a hard phrase from writing 4 lapses in one lesson (rate×16) and monopolizing future lessons. Word trials keep current behavior.
9. **Dictionary layer exposes `:pos`** in completions (adapter stops discarding it; port passes it through). Badge/mode routing keys on pos=phrase AND multi-word — single-word phrase lemmas (hallo, hey) stay words.

## Risks / Trade-offs

- [Space heuristic misfires on multi-word lemmas not in completions yet] → article/`sich` exceptions + suggestion-pos override + chip escape hatch; wrong mode costs one tap.
- [Phrases fail more often and dominate the 3-slot lesson pick] → observe; a per-lesson phrase quota is a one-line follow-up.
- [Self-typed long answers frustrate users] → the correct answer stays visible in the existing error footer. Getting a second attempt without retyping, and showing where the mistake is, come later — both have to serve every trial type, not phrases alone.
- [Autogrow via `field-sizing: content` unsupported on Safari] → JS scrollHeight fallback is part of v1, not an afterthought.
- [One value cannot be held as both a word and a phrase] → accepted: nobody could name a case for it. In exchange the kind became correctable, which a namespace could never offer.

## Migration Plan

None required: existing documents have no `kind` and are read as words. Rollback = revert; a phrase written by a newer client reads as an ordinary word on an older one, which is the least surprising way to degrade.

## Open Questions

- None open. The namespace question was settled on review: ADR-0011 records it.
