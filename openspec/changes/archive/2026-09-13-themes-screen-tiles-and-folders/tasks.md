## 1. Data

- [x] 1.1 `use-cases.collections/summary` reads the collections list and `:words/count` only; returns `{:active-id :items :total-words}` with neutral items
- [x] 1.2 `use-cases.collections/scope-word-ids`: the distinct union of a collection's word-ids and its children's by name; `vocabulary/list-active` scopes through it; `create!` and the parent lookup share one name-equality function; unit tests for the union

## 2. Presenter

- [x] 2.1 Grouping by `/` (folder key trimmed, deeper `/` kept in row text, a single child is still a folder), locale-aware case-insensitive sort, «Всё подряд» pinned first, union count without duplicates, palette class by position, active/editing predicates
- [x] 2.2 One flat sequence of tiles for the view; the columns are the stylesheet's
- [x] 2.3 Node tests for 2.1 and 2.2

## 3. Screen

- [x] 3.1 View: masonry of tiles and folder tiles, each tappable target a `[data-collection-id]`, long press and tap recovery kept, header of a folder without a document is a label and no target; floating «+»; loading state kept
- [x] 3.2 CSS: tiles, folder rows, masonry columns at 2 / 4, floating button, grid bottom inset; the preview, dashed frame and 9:16 rules removed

## 4. Verification

- [x] 4.1 `test/browser/collections-loading.spec.js` on the new DOM; a browser spec seeding `Kurs`, `Kurs / Kapitel 1`, `Kurs / Kapitel 2`, `Grammatik / Konnektoren`: folder tiles, union count, `Grammatik` as a label header carrying its children's union count and writing no document when tapped, twelve or more collections visible at 384 × 800
- [x] 4.2 Screenshots at 384 × 800 and 1280 × 800 for the PR
