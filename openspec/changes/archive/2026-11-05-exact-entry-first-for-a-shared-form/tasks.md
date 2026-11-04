## 1. Test first

- [x] 1.1 `test/backend/examples_test.clj`: nine lemmas share a form in `dictionary-lemmas`; a deftest asks the lookup for it and checks the order (the exact entries first, the rest by rank) and the lemma `lookup-word-meta` takes with no matching gloss; it fails on master

## 2. Fix

- [x] 2.1 `utils/distinct-by` keeps the first element per key in input order, calling `f` once per element
- [x] 2.2 `merge-dictionary-entry-docs` drops its `vec`

## 3. Verify

- [x] 3.1 The new test and the rest of `test/backend/examples_test.clj` pass
- [x] 3.2 On the built dictionary, the lookup for «sie» answers `sie`, `der Sie`, `die Sie` first, and `lookup-word-meta` with no gloss takes `sie`
