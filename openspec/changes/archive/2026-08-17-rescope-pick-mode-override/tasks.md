## 1. Domain

- [x] 1.1 `add-mode` honours the pick only while the normalized value equals the picked lemma
- [x] 1.2 Unit tests: pick held on the picked value, dropped after an edit, `das heißt` still a phrase, no pick at all unchanged

## 2. Home page

- [x] 2.1 `select-item` records the picked lemma alongside the mode
- [x] 2.2 Presenter and `:action/add-word` pass the pick through unchanged

## 3. Verification

- [x] 3.1 `npx shadow-cljs compile node-test` — 188 tests, 445 assertions, 0 failures
- [x] 3.2 Browser: `test/browser/picked-suggestion-mode.spec.js` types `Haus`, picks `das Haus`, appends ` ist gross`, and the panel title turns into "Добавить фразу"
