## Why

Users meet useful German multi-word expressions ("auf jeden Fall", "Wie geht's?") that they want to learn as a unit, but the app only supports single dictionary words. A phrase is not a vocab word and not an example of one — it is "a word and an example in one": user-authored content with its own spaced-repetition life.

## What Changes

- New replicated document type `phrase` (`phrase:<normalized>`) with its own review log; translation stored as a single string (never split by `parse-translations`).
- The home add form detects word vs phrase automatically (space heuristic with article/`sich`/dictionary-lemma exceptions; picking a suggestion decides by its pos) with a tappable chip as override. Both inputs become auto-growing textareas.
- Dictionary completions stop discarding `:pos`; multi-word pos=phrase suggestions get a badge and switch the form into phrase mode.
- Words page shows phrases in the same list with a badge and line wrapping; counts, search, and collections include them.
- Lesson gains a phrase trial: exact typing required (RU→DE), grading through a new typography-only normalization (`normalize-for-grading`), token-level diff on error, "ИСПРАВИТЬ" retry that preserves the answer, and at most one review write per trial per lesson.
- No LLM example generation for phrases; client conflict resolver and import LWW extended to the `phrase` doc type.

## Capabilities

### New Capabilities
- `user-phrases`: phrase entity lifecycle — authoring with automatic word/phrase mode detection, storage and sync, words-list presence, phrase lesson trial with exact-typing grading, diff feedback, and per-lesson review discipline.

### Modified Capabilities
- `data-model`: new replicated `phrase` document type with single-string translation semantics (no comma splitting), content-addressed id in the `phrase:` namespace.
- `lesson`: lesson word selection draws from vocab and phrases as one pool; phrase trials require an exact typed answer and write reviews to the phrase's own log.
- `dictionary-storage`: the completions capability exposes `:pos` for each suggestion.

## Impact

- Client: `db/pouch.cljs` (doc-type→db map), `domain/vocabulary.cljs` bypasses, new `domain/phrase.cljs` + `use_cases/phrase.cljs`, `adapters/progress_store.cljs` (union queries), `adapters/dictionary.cljs` + `ports/dictionary.cljs` (`:pos`), `sync.cljs` (conflict resolver), `data_export.cljs` (import LWW), pages `home`/`words`/`lesson`, `domain/lesson.cljs`, `shared/utils` (`normalize-for-grading`), CSS blocks `home`, `lesson`, `word-item`, `input`.
- Backend: untouched (no example fetch for phrases means no new API).
- Dictionary pipeline and SQLite artifact: untouched.
- Sync: phrases replicate through the existing user-db replication; CouchDB schema unchanged.
