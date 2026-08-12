## Context

Users want to learn multi-word German expressions as units. Today the client only supports single dictionary words: the home form is a pair of single-line inputs with lemma autocomplete, vocab docs are `vocab:<normalized>` (ADR-0008), lessons type-check RU→DE answers against `normalize-german` equality, and every added word queues LLM example generation. The owner's decisions: a phrase is an exceptional first-class entity ("a word and an example in one") with its own SRS log; lesson answers must be typed fully correctly (no self-check); multi-line input is required; the dictionary's 5206 pos=phrase lemmas must be accounted for.

In-force ADRs constraining this design: 0008 (content-addressed ids, frozen `normalize-german`, conflict resolution unions translations), 0005 (client runtime system/component graph), 0007/0009 (sync triggers — untouched). None is superseded by this change; 0008 is extended, not revisited.

## Goals / Non-Goals

**Goals:**
- Phrase document type with its own review log, replicated and conflict-resolved like vocab.
- Frictionless authoring: automatic word/phrase mode detection, auto-growing textareas.
- Phrase lesson trial with exact typing, token diff on error, preserved answer on retry.
- Dictionary pos=phrase suggestions flow into the new entity.

**Non-Goals:**
- No LLM translation or example generation for phrases.
- No mass import of dictionary phrases.
- No migration of existing multi-word vocab docs (they stay words; a phrase with the same value is a separate entity).
- No changes to dictionary pipeline, SQLite artifact, backend API, or CouchDB schema.
- No change to word-trial review discipline.

## Decisions

1. **Doc type `phrase`, id `phrase:<normalize-german(value)>`** (extends ADR-0008 to a second namespace). Content-addressing keeps cross-device dedup free; spaces stay in the id. Alternative — `phrase:<uuid>` with editable text — rejected: second id discipline, manual dedup, and vocab already fixed value-immutability (GH-96).
2. **Translation is a single entry, never split.** `parse-translations` (splits on `[,;.]`) is bypassed on add, conflict merge, and edit — a sentence translation must survive punctuation. Conflict resolution unions translation entries (ADR-0008 semantics) without splitting values.
3. **Registration over branching:** `"phrase"` joins the doc-type→db map (`db/pouch.cljs`) routing to user-db; replication, export, collections (`:word-ids` hold opaque ids) work unchanged. Vocab-only selects in `adapters/progress_store.cljs` become unions of two `find-all` calls (Mango `$in` breaks `db-for` routing). Client conflict resolver adds a `phrase:` range pass; import LWW branch admits `"phrase"`.
4. **Separate use-case `phrase-add!`** rather than flags inside `vocabulary/add!`: dedup in the phrase namespace, single-entry translation merge, initial review seed, collection membership, and no `examples/request!` (a phrase is its own example; also avoids backend `same-lemma?` validation burning LLM retries on multi-word lemmas).
5. **Automatic mode detection, no manual toggle.** Space in trimmed input → phrase, except article+word (der/die/das/ein/eine), `sich`+word, or input matching a non-phrase dictionary lemma from already-fetched completions; picking a suggestion decides by its pos (multi-word pos=phrase → phrase). Tappable chip appears only when phrase is detected, as override until the form clears. Both fields are always auto-growing textareas (rows=1 compact) so a mode flip never remounts inputs or loses text. Mode flip must clear open suggestions and guard stale async completion responses. Alternative — explicit segment toggle — rejected by owner as tiresome.
6. **Grading normalization is a new function, `normalize-for-grading`** in shared utils — `normalize-german` is a frozen id contract (ADR-0008) and must not change. Forgiveness is typography-only: apostrophes (U+0027/U+2019/U+02BC) deleted, unicode quotes/dashes → space. Words are never forgiven.
7. **Token diff over normalized tokens** (LCS, statuses match/wrong/extra/missing) computed in domain/presenter; view renders segments. Both strings tokenize after `normalize-for-grading`, guaranteeing alignment. Char-level markers deferred.
8. **Review discipline for phrase trials: one review per trial-id per lesson**, tracked as `:reviewed-trial-ids` in lesson state — not a flag on the trial (trials are compared by value in `remove-trial`). Prevents a hard phrase from writing 4 lapses in one lesson (rate×16) and monopolizing future lessons. Word trials keep current behavior.
9. **Dictionary layer exposes `:pos`** in completions (adapter stops discarding it; port passes it through). Badge/mode routing keys on pos=phrase AND multi-word — single-word phrase lemmas (hallo, hey) stay words.

## Risks / Trade-offs

- [Space heuristic misfires on multi-word lemmas not in completions yet] → article/`sich` exceptions + suggestion-pos override + chip escape hatch; wrong mode costs one tap.
- [Phrases fail more often and dominate the 3-slot lesson pick] → observe; a per-lesson phrase quota is a one-line follow-up.
- [Self-typed long answers frustrate users] → diff shows where the mistake is, retry preserves the answer, correct answer stays visible (existing error footer).
- [Union rewrite of progress-store selects regresses word queries] → unit tests over union queries; browser pass on /words and lesson start.
- [Autogrow via `field-sizing: content` unsupported on Safari] → JS scrollHeight fallback is part of v1, not an afterthought.
- [Cross-type duplicates (word "auf jeden Fall" + phrase)] → both exist, each with its own review log; no warning, no conversion in v1.

## Migration Plan

None required: new doc type only, no existing data rewritten. Rollback = revert; phrase docs left in user-db are ignored by older clients except in generic export (type-agnostic, harmless).

## Open Questions

- Whether ADR step should record an amendment ADR extending 0008's content-addressing contract to the `phrase:` namespace (recommended: yes, small ADR referencing 0008 rather than superseding it).
