## Context

`src/backend/examples/dictionary.clj` is the only reader of `dictionary-db`. It makes three CouchDB calls: an `_index` at boot, an exact `_find` on `type` + `meta.normalized_value` (limit 20), and a `sf:<form>` document read followed by `_all_docs` for the entries. Results are deduped by `:_id`; callers use `:pos`, `:translation` (`lang`, `value`) and `meta.cefr_level`.

What `resources/dictionary/dictionary.sqlite` holds (checked, 158 294 lemmas):

- `lemmas(id, value, pos, rank)`: `value` keeps case and article ("der Operettensänger"); no index beyond the primary key.
- `translations(lemma_id, rank, value)`: no language column; `labels` / `translation_labels` for labels.
- `surface_forms(normalized_form, lemma_id)`, primary key leads with `normalized_form`. The key is `normalize-german` (lower case, ä→ae, ö→oe, ü→ue, ß→ss) of the value, of the value without its article, and of the inflected forms.
- **No CEFR level.** It exists only in `enrichment-meta.jsonl`, keyed by a text id the SQLite file deliberately omits.

The backend already finds the file: `core.clj` serves it from `LEARNING_APP__DICTIONARY_DIR`, else from the jar resource `dictionary/dictionary.sqlite`; `build.clj` copies `resources/` into the jar. A JDBC connection cannot open a file inside a jar, so a jar-resident file must be copied out once.

## Decisions

- The lookup keeps its public shape (`lookup-dictionary-entries`, `lookup-word-meta`); rows are mapped to the entry shape callers already read.
- The SQLite dictionary is read-only for the backend; nothing in the backend writes to it.
- The dictionary tool pipeline (`clojure -X:dictionary build`) stays; only the CouchDB import branch is cut.

## Open decisions

1. **How the backend obtains `dictionary.sqlite`.**
   (A) The source the client route already uses: `LEARNING_APP__DICTIONARY_DIR` if set, else the jar resource copied once to a temp file. (B) A path populated by deploy outside the jar.
   Recommend A: one source for both readers, no new deploy step, and the file is already in the jar.
2. **How the `sf:` lookup maps to SQLite.**
   (A) Existing `surface_forms`, queried by `normalize-german` of the word, then `lemmas` by id. (B) A new table.
   Recommend A: it holds the same forms, keyed for a clustered lookup. The folding is wider than the old lowercase key (`abhängen` and `abhaengen` meet); to confirm against the old `sf:` documents.
3. **How the normalized-value (exact) lookup maps to SQLite.**
   (A) Candidates from `surface_forms`, then keep lemmas whose `lower(value)` equals the word, so "die Leiter" stays distinct from "der Leiter". (B) Add an index on `lower(value)` in the artifact.
   Recommend A: no artifact change; B changes the file every client downloads.
4. **An existing `dictionary-db` on a deployed server.**
   (A) Left in place; `postinst` stops creating it and the runbook says how an operator drops it. (B) Dropped by `postinst`.
   Recommend A: deleting data from a package script is irreversible, and nothing reads it once the route is gone.
5. **Dictionary file missing or unreadable.**
   (A) Warn and treat every word as unknown. (B) Fail fast at boot.
   Recommend B: with a local file nothing is transiently down, and A is the silent loss of part of speech and CEFR the old spec warned about. The delta spec states B.
6. **CEFR level, absent from the SQLite file** (found while checking; not in the brief).
   (A) Add a nullable `cefr_level` column to `lemmas` at build time. (B) Backend reads `enrichment-meta.jsonl` and matches by value + pos. (C) A backend-only sidecar file.
   Recommend A: one file, one join; B matches homographs by guesswork and loads 40 MB into memory. Cost of A: the artifact hash changes, so clients download the dictionary once more when it ships.

## Risks

- Umlaut folding in `surface_forms` widens matches against CouchDB → compare answers for a sample of words on both before the importer is deleted.
- Jar copy-out writes a temp file at boot → do it once, and fail fast (decision 5).
