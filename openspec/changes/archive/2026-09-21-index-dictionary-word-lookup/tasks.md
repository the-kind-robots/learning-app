## 1. Index creation

- [x] 1.1 Add the index-ensuring call to `src/backend/examples/dictionary.clj`, beside the lookup it serves
- [x] 1.2 Call it from `serve!` in `src/backend/core.clj`, best-effort, alongside the reconciliation report

## 2. Tests

- [x] 2.1 Cover the index request the backend issues at boot
- [x] 2.2 Cover that an unreachable dictionary database does not fail the call
- [x] 2.3 Cover that `lookup-word-meta` returns the part of speech and CEFR level of a matched entry, and the unknown-word outcome
- [x] 2.4 Run the backend test suite

## 3. Verification against the dev stand

- [x] 3.1 Record the query plan and timing of the lookup before the index
- [x] 3.2 Create the index and record the plan and timing after
- [x] 3.3 Measure the surface-form path for comparison
