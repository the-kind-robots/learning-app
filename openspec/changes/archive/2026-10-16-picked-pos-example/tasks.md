## 1. Specs

- [x] 1.1 Add the vocabulary `pos` and the request `pos` requirements.

## 2. Implementation

- [x] 2.1 Home keeps the picked `pos` with the picked lemma; `add!` stores it on the word.
- [x] 2.2 The fetcher sends `pos`; the backend lookup follows it.

## 3. Verify

- [x] 3.1 Backend unit cases: `clojure -M:test`.
- [x] 3.2 Browser spec: `test/browser/picked-pos-example.spec.js`.
