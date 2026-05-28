## Why

The functional collections feature shipped under `themes-feature` (archived 2026-05-28) lands the data model, navigation, lookup semantics, and backend prompt injection. The visual layer is currently a minimal grid of name+count cards. The accompanying design source (Safari Tab Grid metaphor, 9:16 cards, Duolingo palette, animated transitions) was not adapted in the first delivery to keep the merge boundary clean.

This change adapts the UI to the committed design source at `openspec/changes/archive/2026-05-28-themes-feature/design-source/` (UX spec, React prototype, CSS, screenshots).

## What Changes

- **Switcher visual + CardPreview**: 9:16 cards; cycled Duolingo palette borders (green / blue / orange / pink / purple / yellow / red / teal by creation order); dashed-SVG "new" tile with green +; each card previews the centered collection name plus the first 14 words with progress dots colored by retention level (red / orange / yellow / green); empty state shows skeleton rows; X-style delete (no confirm dialog) via long-press editing state; per-card editing only.
- **Header icon morph** grid ↔ × — on the switcher, the right-corner icon becomes a close button that returns home.
- **Drag-reorder** with a stored `:order` field on collection docs; main "Всё подряд" card is locked to position 0; long-press → editing → drag.
- **Main card rename to "Всё подряд"**: rename the `all-words` sentinel concept's display label to "Всё подряд"; fixed, immutable name; on home, do NOT render the H1 collection-name input for the main card (jump straight to the add-word form); the name appears only inside CardPreview.
- **View Transitions API** for home ↔ switcher zoom (paired `view-transition-name` on the tapped card and the content area).

## Capabilities

### Modified Capabilities

- `collections-navigation`: card visual treatment, CardPreview, palette, drag-reorder, header icon morph, View Transitions, delete UX (no confirm).
- `collections-data-model`: add `:order` field on collection documents; main card locked at order 0.

### Modified Behaviour

- Delete confirmation dialog from the functional release is removed — delete proceeds immediately from the editing-state X control.
- Home screen no longer shows an inline H1 rename for the main card.

## Impact

- `src/client/pages/collections/view.cljs` — card layout, palette, CardPreview, drag handlers, editing state, delete-without-dialog
- `src/client/pages/collections/actions.cljs` — drop confirm-delete / cancel-delete; add reorder action
- `src/client/pages/collections/effects.cljs` — drop delete-confirm flow; add reorder persist
- `src/client/adapters/collections.cljs` — `:order` field on create, list returns ordered
- `src/client/pages/home/view.cljs` — gate H1 rename to non-main cards only
- `resources/public/css/pages/collections.css` — 9:16 cards, palette tokens, view-transition styles
- `src/client/application.cljs` — wire close-icon morph on `:page/collections` (already done in the functional change)

## Source Design

See `openspec/changes/archive/2026-05-28-themes-feature/design-source/` for the UX spec, React prototype, CSS, and screenshots that drive this redesign.
