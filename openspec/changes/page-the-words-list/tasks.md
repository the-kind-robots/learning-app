## 1. Use case

- [ ] 1.1 `use-cases.vocabulary/list` returns `:matches` — the rows left after the search
  filter, before paging — beside the pre-filter `:total`

## 2. Presenter

- [ ] 2.1 `page-size` (50) and `next-limit` in `pages.words.presenter`
- [ ] 2.2 `page-state` returns `:words/limit` and `:words/more?`

## 3. Actions and effects

- [ ] 3.1 One list call in `pages.words.effects`, taking `{:search :limit}`
- [ ] 3.2 `:action/show-more-words` asks for the next page; search and mutations carry the
  limit they should land on
- [ ] 3.3 `:effect/observe-words-sentinel` / `:effect/unobserve-words-sentinel`

## 4. View and CSS

- [ ] 4.1 Sentinel row rendered while `:words/more?`
- [ ] 4.2 `.word-list__sentinel` back in use

## 5. Verify

- [ ] 5.1 Node tests: presenter paging props, use-case `:matches`
- [ ] 5.2 Browser: 50 rows on first render, append on scroll, reset on search, preserved
  across an edit
