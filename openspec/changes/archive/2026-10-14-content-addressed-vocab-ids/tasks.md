## 1. Content-addressed id

- [x] 1.1 In `domain/vocabulary.cljs`, set the vocab `_id` to `"vocab:" + normalize(value)` in `new-word`
- [x] 1.2 Freeze the `normalize` contract — add tests pinning current normalization (articles, case, umlauts)
- [x] 1.3 Simplify `find-duplicate` to get-or-create by `_id` (keep normalization identical to the id scheme)

## 2. Conflict resolution

- [x] 2.1 Add a vocab conflict resolver that unions translations via `merge-translations` instead of plain LWW

## 3. Verify

- [x] 3.1 Two devices add the same word → same `_id` → after sync one doc, no duplicate
- [x] 3.2 Two devices add the same word with different translations → resolved doc has the union, nothing dropped
- [x] 3.3 `npx shadow-cljs compile app` clean and node tests green
