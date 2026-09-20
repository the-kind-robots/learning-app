## 1. Specs

- [x] 1.1 Drop the selection sentence from the lesson trial-generation requirement.
- [x] 1.2 Add the lesson selection requirement to the lesson spec.

## 2. Implementation

- [x] 2.1 Add the urgency function to `domain.retention`.
- [x] 2.2 Sort `use-cases.vocabulary/list` by urgency.
- [x] 2.3 Add the pool constant and the pure picker to `domain.lesson`.
- [x] 2.4 Have `use-cases.lesson/start!` read a pool and pick from it.

## 3. Verify

- [x] 3.1 Cover urgency ordering past the point where retention underflows.
- [x] 3.2 Cover the picker: subset, count, no repeats, short pool.
- [x] 3.3 Regression: a lesson over all-zero-retention words is not the alphabetically first ones.
- [x] 3.4 `npx shadow-cljs node-test`.
