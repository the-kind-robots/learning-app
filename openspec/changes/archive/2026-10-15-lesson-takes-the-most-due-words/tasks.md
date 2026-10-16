## 1. Specs

- [x] 1.1 Replace the pool draw with taking the most due.

## 2. Implementation

- [x] 2.1 `pick-vocab` keeps the `n` most due with a random tie key; drop the pool size and the shuffle.
- [x] 2.2 Drop `:vocab-pool-size` from `use-cases.lesson/start`.

## 3. Verify

- [x] 3.1 Cover the most due words being taken over a fresh one, ties still random, strict urgency winning.
- [x] 3.2 `npx shadow-cljs compile node-test` and run it.
