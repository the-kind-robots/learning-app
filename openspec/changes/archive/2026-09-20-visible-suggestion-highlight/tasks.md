# Tasks

## 1. The mark

- [x] 1.1 `suggestion-props` in `pages.home.presenter` takes the active index
      and the item's position and adds `:active?` beside `:phrase?`
- [x] 1.2 `suggestions-props` drops `:active` from the props: with the
      predicate on each item nothing reads it
- [x] 1.3 `suggestion-item` in `pages.home.view` renders `data-active` from the
      item's own `:active?`; the view compares nothing
- [x] 1.4 `:suggestions/active` leaves the state map in `pages.home.actions` —
      the index is the single source of truth

## 2. Verify

- [x] 2.1 Presenter unit test in `test/client/pages/home_test.cljs`: the props
      mark exactly one item, the one the active index names, and a fresh list
      marks the first
- [x] 2.2 Browser spec `test/browser/suggestion-highlight.spec.js`: type a
      prefix, `ArrowDown` moves `[data-active]` to the next entry, `ArrowUp`
      brings it back — real Chrome against the fixture dictionary
- [x] 2.3 The browser spec is a net, not a tautology: run against the code
      before the fix, its three highlight tests fail (`data-active` resolves to
      0 elements) while the fourth — the entry `Enter` picks — passes, since
      the index arithmetic was never the defect
- [x] 2.4 `:effect/scroll-nearest` finds `.suggestions [data-active]` after the
      fix: the literal production selector resolves to exactly one element
- [x] 2.5 Browser suite green (37 passed); node suite green (227 tests, 540
      assertions, 0 failures)
