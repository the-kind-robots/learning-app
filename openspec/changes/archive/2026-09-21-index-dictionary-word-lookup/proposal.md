## Why

The lookup that gives the generation prompt a word's part of speech and CEFR level queries
`dictionary-db` by `meta.normalized_value`, and no index covers that field. CouchDB falls back to
scanning the whole dictionary, the query outlives its HTTP timeout, and the lookup returns nil —
which the caller cannot tell apart from "the word is not in the dictionary". Every example is
generated without a part of speech and at the default CEFR level, and the only trace is a warning.

## What Changes

- The backend creates, at boot, the Mango index the word lookup queries by, so the lookup answers
  from an index instead of a scan.
- A generation request for a word the dictionary knows carries that word's part of speech and CEFR
  level.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `examples`: states that a generation request carries the dictionary's part of speech and CEFR
  level for a known word, and that the lookup answers within its timeout rather than degrading to
  the unknown-word path.

## Impact

- `src/backend/examples/dictionary.clj` — index creation beside the query it serves.
- `src/backend/core.clj` — boot calls it, best-effort, like the reconciliation report.
- `test/backend/examples_test.clj` — coverage for the index request and for the lookup's result.
