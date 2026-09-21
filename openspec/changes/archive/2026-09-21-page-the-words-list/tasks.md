## 1. Use case

- [x] 1.1 `use-cases.vocabulary/list` returns `:matches` — the rows left after the search
  filter, before paging — beside the pre-filter `:total`
- [x] 1.2 `:order :alphabetical` reads the page off the id-keyed preview view;
  `:order :most-due` keeps the urgency ranking and its full reads (GH-431)
- [x] 1.3 Retention levels read by key, for the rows of the page only
- [x] 1.4 `:total` from the view's row count (`{:limit 0}`), or the membership's length in
  a collection

## 2. Presenter

- [x] 2.1 `page-size` (50) and `next-limit` in `pages.words.presenter`
- [x] 2.2 `page-state` returns `:words/limit` and `:words/more?`

## 3. Actions and effects

- [x] 3.1 One list call in `pages.words.effects`, taking `{:search :limit}`
- [x] 3.2 `:action/show-more-words` asks for the next page; search and mutations carry the
  limit they should land on
- [x] 3.3 `:effect/observe-words-sentinel` / `:effect/unobserve-words-sentinel`
- [x] 3.4 `:page/load` carries the limit and query of the rows on screen, so the reload a
  sync pull triggers reads the same page

## 4. View and CSS

- [x] 4.1 Sentinel row rendered while `:words/more?`
- [x] 4.2 `.word-list__sentinel` back in use

## 5. Verify

- [x] 5.1 Node tests: presenter paging props, use-case `:matches`, the stored reload effect
- [x] 5.2 Browser: 50 rows on first render, append on scroll, reset on search, preserved
  across an edit
- [x] 5.3 Measured click → first row on 1503 words / 7100 reviews, warm, three times,
  before and after
