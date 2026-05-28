## 1. Visual treatment — 9:16 cards and palette

- [ ] 1.1 Lay out cards at 9:16 aspect ratio; min/max width tuned for mobile + tablet
- [ ] 1.2 Add Duolingo palette tokens to `colors.css`: green / blue / orange / pink / purple / yellow / red / teal
- [ ] 1.3 Border color cycles through palette by collection creation order; main card uses neutral border
- [ ] 1.4 Dashed-SVG "new" tile with green `+` symbol

## 2. CardPreview

- [ ] 2.1 Inside each card render centered collection name and first 14 words
- [ ] 2.2 Each word row has a progress dot colored by retention level (red / orange / yellow / green)
- [ ] 2.3 Empty state renders 3 skeleton rows
- [ ] 2.4 Source word data via a presenter that returns ordered, capped vocab per collection

## 3. Editing state and delete UX

- [ ] 3.1 Long-press enters per-card editing state (not page-level)
- [ ] 3.2 X-style delete control in editing state — fires delete immediately, no confirmation dialog
- [ ] 3.3 Remove `confirm-delete-collection` / `cancel-delete-collection` actions and the dialog from `effects.cljs`
- [ ] 3.4 Tap outside the editing card exits editing state

## 4. Drag-reorder

- [ ] 4.1 Add `:order` field to collection documents; default to `(count existing-collections)` on create
- [ ] 4.2 `list-collections` returns docs sorted by `:order`
- [ ] 4.3 Main "Всё подряд" card pinned to position 0; not draggable
- [ ] 4.4 Pointer-based drag handler inside editing state — swap orders on drop; bulk-update affected docs

## 5. Main card label "Всё подряд"

- [ ] 5.1 Replace the All Words display label "Все слова" with "Всё подряд" wherever it surfaces (collection list, card preview, etc.)
- [ ] 5.2 Confirmation dialog text from `collections-navigation` (if it survives) uses "Слова останутся в «Всё подряд»."
- [ ] 5.3 On the home screen, do NOT render the inline H1 rename for the main card — keep the rename input only for named collections

## 6. Header icon morph

- [ ] 6.1 On `:page/collections` render the close (×) variant of the corner icon
- [ ] 6.2 On `:page/home` render the grid variant
- [ ] 6.3 Already implemented as part of the functional release — verify it still holds after the redesign

## 7. View Transitions API

- [ ] 7.1 Add `view-transition-name` to the tapped collection card and the home content slot
- [ ] 7.2 Wrap the navigation transition with `document.startViewTransition` where supported
- [ ] 7.3 Define `::view-transition-old(*)` / `::view-transition-new(*)` styles for zoom

## 8. Verification

- [ ] 8.1 `npx shadow-cljs compile app` passes
- [ ] 8.2 Manual: create / rename / delete / reorder works without the confirm dialog
- [ ] 8.3 Manual: home → switcher → home transition runs with View Transitions on Chromium
- [ ] 8.4 Manual: CardPreview shows correct retention-color dots and skeleton in empty state
