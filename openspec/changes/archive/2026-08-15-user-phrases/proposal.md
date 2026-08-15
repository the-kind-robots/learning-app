## Why

Users meet useful German multi-word expressions ("auf jeden Fall", "Wie geht's?") that they want to learn as a unit, but the app only supports single dictionary words. A phrase is not a vocab word and not an example of one — it is "a word and an example in one": user-authored content with its own spaced-repetition life.

## What Changes

- A phrase is a vocabulary document with `kind: "phrase"` (ADR-0011): same `vocab:<normalized>` id, same review log, translation stored as a single string (never split by `parse-translations`). A document without a kind is a word, so nothing is migrated.
- The home add form detects word vs phrase automatically (space heuristic with article/`sich`/dictionary-lemma exceptions; picking a suggestion decides by its pos), with no control of its own — the legend and the label state the mode. Both inputs become auto-growing textareas.
- Dictionary completions stop discarding `:pos`; multi-word pos=phrase suggestions get a badge and switch the form into phrase mode.
- Words page shows phrases in the same list with line wrapping and no type label; counts, search, and collections include them.
- Lesson gains a phrase trial: exact typing required (RU→DE), grading through a new typography-only normalization (`normalize-for-grading`), and a review written per graded attempt, as words already do. The error footer is the existing one, unchanged.
- No LLM example generation for phrases; the conflict resolver and import need no new branch, since a phrase is a vocabulary document.

## Capabilities

### New Capabilities
- `user-phrases`: phrase entity lifecycle — authoring with automatic word/phrase mode detection, storage and sync, words-list presence, and a phrase lesson trial with exact-typing grading.

### Modified Capabilities
- `data-model`: vocabulary documents gain a `kind`; phrases are the `"phrase"` kind, with single-string translation semantics (no comma splitting) and the existing content-addressed id.
- `lesson`: lesson word selection draws from vocab and phrases as one pool; phrase trials require an exact typed answer and write reviews to the phrase's own log.
- `dictionary-storage`: the completions capability exposes `:pos` for each suggestion.

## Impact

- Client: new `domain/phrase.cljs` + `use_cases/phrase.cljs`, `domain/vocabulary.cljs` bypasses, `adapters/dictionary.cljs` + `ports/dictionary.cljs` (`:pos`), pages `home`/`words`/`lesson`, `domain/lesson.cljs`, `shared/utils` (`normalize-for-grading`), CSS blocks `home`, `lesson`, `word-item`, `input`. Storage, sync, routing and import are untouched: a phrase is a vocabulary document.
- Backend: untouched (no example fetch for phrases means no new API).
- Dictionary pipeline and SQLite artifact: untouched.
- Sync: phrases replicate through the existing user-db replication; CouchDB schema unchanged.
