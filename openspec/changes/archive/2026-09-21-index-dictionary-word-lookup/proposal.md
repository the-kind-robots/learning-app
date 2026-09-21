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
- The dictionary import restarts the application when it succeeds, and the dictionary deploy fails
  when the lookup is still planned against a scan — a reset import drops the database and the index
  with it, so without this the defect returns at every dictionary deploy.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `examples`: states that a generation request carries the dictionary's part of speech and CEFR
  level for a known word, and that the lookup answers within its timeout rather than degrading to
  the unknown-word path.
- `deploy-pipeline`: states that a dictionary reload ends with the application reading through that
  index, and that the deploy checks it instead of assuming it.

## Impact

- `src/backend/examples/dictionary.clj` — index creation beside the query it serves.
- `src/backend/core.clj` — boot calls it, best-effort, like the reconciliation report.
- `test/backend/examples_test.clj` — coverage for the index request and for the lookup's result.
- `infra/production/etc/systemd/system/learning-app-dictionary-import.service` — restarts the app on
  a successful import.
- `.github/workflows/deploy-dictionary.yml` — smoke check on the lookup's query plan.
- `docs/ops/runbook.md` — the manual import step now says what it also does.
