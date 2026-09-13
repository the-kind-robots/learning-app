## 1. Data

- [ ] 1.1 `use-cases.collections/summary` reads the collections list and `:words/count` only; returns `{:active-id :items :total-words}` with neutral items
- [ ] 1.2 `use-cases.collections/scope-word-ids`: the distinct union of a collection's word-ids and its children's by name; `vocabulary/list-active` scopes through it; `create!` and the parent lookup share one name-equality function; unit tests for the union

## 2. Presenter

- [ ] 2.1 Grouping by `/` (folder key trimmed, deeper `/` kept in row text, a single child is still a folder), locale-aware case-insensitive sort, «Всё подряд» pinned first, union count without duplicates, palette class by position, active/editing predicates
- [ ] 2.2 Column placement: N flex columns, each tile into the lightest by estimate (plain 1, folder 1 + 0.5 per row); `:collections/columns` from the viewport width on load and on resize
- [ ] 2.3 Node tests for 2.1 and 2.2

## 3. Screen

- [ ] 3.1 View: masonry of tiles and folder tiles, each tappable target a `[data-collection-id]`, long press and tap recovery kept, header of a folder without a document creates it; floating «+»; loading state kept
- [ ] 3.2 CSS: tiles, folder rows, masonry columns at 2 / 4, floating button, grid bottom inset; the preview, dashed frame and 9:16 rules removed

## 4. Verification

- [ ] 4.1 `test/browser/collections-loading.spec.js` on the new DOM; a browser spec seeding `Kurs`, `Kurs / Kapitel 1`, `Kurs / Kapitel 2`, `Grammatik / Konnektoren`: folder tiles, union count, tapping `Grammatik` creates the document, twelve or more collections visible at 384 × 800
- [ ] 4.2 Screenshots at 384 × 800 and 1280 × 800 for the PR
