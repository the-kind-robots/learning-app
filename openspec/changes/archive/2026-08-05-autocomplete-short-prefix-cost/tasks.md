# Tasks

- [x] 1. Rewrite `completions-sql`: top-ten lemmas first, then per-row `has_exact` probe and translations aggregate
- [x] 2. Update the `completions` bind order to match
- [x] 3. Equivalence: diff old vs new sqlite3 output for f, fe, fen, fenster, sch, a, zu, ue — identical
- [x] 4. Benchmark warm-cache native: f 124 ms → ~22 ms, fe ~15 ms → ~4 ms, fenster ≤1 ms both
- [x] 5. `shadow-cljs compile app node-test` 0 warnings; `node target/node-tests.js` 0 failures (no test exercises completions; in-app check pends the dev stand)
