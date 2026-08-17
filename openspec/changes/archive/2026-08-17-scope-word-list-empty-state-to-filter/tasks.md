## 1. Presenter

- [x] 1.1 Add the empty-state props to `pages.words.presenter`: rows plus first-run / no-matches text, hint and CTA, derived from the scope total and the filtered rows
- [x] 1.2 Have `:action/show-words` store the presented props in state

## 2. View

- [x] 2.1 Render the word list from the ready props: no `empty?`, `seq` or search comparison left in `pages.words.view`
- [x] 2.2 Keep header, search box and lesson footer on screen whenever the vocabulary is not empty

## 3. Verification

- [x] 3.1 Presenter unit tests for the three outcomes: empty vocabulary, filter with no matches, filter with matches
- [x] 3.2 `npx shadow-cljs node-test` green
- [x] 3.3 Browser check of both empty states with screenshots
