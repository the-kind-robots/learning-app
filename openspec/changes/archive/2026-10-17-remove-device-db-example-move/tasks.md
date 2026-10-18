## 1. Specs

- [x] 1.1 Remove the move requirement and the sweep from `data-model`; drop the move from `example-backfill` and `learner-data-memory`.

## 2. Implementation

- [x] 2.1 Remove `tidy-device-db!`, the example-move capabilities, the sweep and the background-migration mechanism, `forget-account-data!`, and the helpers only they used.

## 3. Verify

- [x] 3.1 Node unit tests: `npx shadow-cljs compile node-test && node target/node-tests.js`.
- [x] 3.2 Browser suite: `npx playwright test`.
