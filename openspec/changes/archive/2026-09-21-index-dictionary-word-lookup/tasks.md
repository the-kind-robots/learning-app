## 1. Index creation

- [x] 1.1 Add the index-ensuring call to `src/backend/examples/dictionary.clj`, beside the lookup it serves
- [x] 1.2 Call it from `serve!` in `src/backend/core.clj`, best-effort, alongside the reconciliation report

## 2. Tests

- [x] 2.1 Cover the index request the backend issues at boot
- [x] 2.2 Cover that an unreachable dictionary database does not fail the call
- [x] 2.3 Cover that `lookup-word-meta` returns the part of speech and CEFR level of a matched entry, and the unknown-word outcome
- [x] 2.4 Run the backend test suite

## 3. Surviving a reload

- [x] 3.1 Restart the application from `learning-app-dictionary-import.service` on a successful import
- [x] 3.2 Check the lookup's query plan in the dictionary deploy and fail when it is a full read
- [x] 3.3 Say in the runbook what the manual import step now also does

## 4. Verification against the dev stand

- [x] 4.1 Record the query plan and timing of the lookup before the index
- [x] 4.2 Create the index and record the plan and timing after
- [x] 4.3 Measure the surface-form path for comparison
- [x] 4.4 Drop the index, run the application's boot path, and show the plan coming back
- [x] 4.5 Run the deploy's plan check against both states and show it discriminates
