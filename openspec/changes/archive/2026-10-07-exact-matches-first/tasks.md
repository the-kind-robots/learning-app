## 1. Frequency index

- [x] 1.1 `read-frequency-file` keeps the first (best) row per normalized key instead of the last
- [x] 1.2 Test in `tools/dictionary/test/dictionary/frequency_test.clj`: `Rücken`/`ruecken` collide, the key carries Rücken's rank and count, in either file order
- [x] 1.3 `clojure -M:test` in `tools/dictionary` green

## 2. Dictionary rebuild

- [x] 2.1 `clojure -X:dictionary build` from the repository root against the Kaikki dump, Goethe list and frequency file already on disk
- [x] 2.2 Report lemma count, file size, number of lemmas whose rank changed, and the ranks of Rücken, für, über, können, schön before and after

## 3. Completion query

- [x] 3.1 `completions-sql`: exact matches by equality on `normalized_form`, union with the rank top ten of the range minus those ids, `ORDER BY exact DESC, rank DESC, value ASC LIMIT 10`; `has_exact` from the union flag
- [x] 3.2 Behaviour line in the e2e suite (`test/browser/suggestions-order.spec.js`): on a fixture where der Rücken is outranked by das Rückenmark, typing «Rücken» lists der Rücken first and prefills «спина»; catalog regenerated
- [x] 3.3 Node test suite and the touched browser specs green
- [x] 3.4 Query time on the shipped dictionary for one-, two- and three-letter prefixes before and after, recorded in the PR

## 4. Close

- [x] 4.1 Archive the change on the branch before the PR
