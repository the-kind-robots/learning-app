## Source

Design artefacts live at `openspec/changes/archive/2026-05-28-themes-feature/design-source/` (UX spec, React prototype, CSS, screenshots), preserved alongside the functional change that shipped the underlying data model.

## Constraints

- The functional `collections-data-model` and `collections-navigation` capabilities are already in production. This change MUST NOT break their contracts; it MAY extend them (e.g. `:order` field, removed delete-confirm dialog).
- Migration: existing collection docs lack `:order`. On first read post-migration, the system assigns deterministic order via creation timestamp.
- View Transitions are a progressive enhancement — Safari/Firefox fall back to instant navigation; no broken state.

## Decisions

### Palette is fixed and creation-ordered

Eight Duolingo-ish colors cycle by creation order. The mapping is deterministic — color = `palette[order mod 8]`. Reordering a card therefore re-colors it, which is intentional (user controls visual placement and the color naturally follows). Main card is excluded from the palette and uses a neutral border.

### Per-card editing state, not page-level

Long-press promotes a single card into editing mode. Tapping elsewhere exits. This sidesteps the modal-dialog UX from the functional release (which felt heavy) and brings the interaction closer to the iOS home-screen jiggle metaphor in the design source.

### Delete without confirmation

Once a card is in editing state, the X control deletes the collection immediately. Rationale: editing state is an explicit destructive context; double-confirm felt redundant in the design source and adds friction. Membership cleanup still runs (words remain in All Words).

### Drag-reorder via `:order` field

Storing order as a numeric field (not derived) keeps the list deterministic across sync, devices, and reloads. Bulk-update on drop touches only the affected swap, not the whole list.

### Main card label change

"Всё подряд" replaces "Все слова" as the user-visible label for the All Words sentinel. The internal id stays `"all-words"` — no migration. The home screen also drops the inline rename input for the main card (it has a fixed name), simplifying that surface.

### View Transitions scoped to the home ↔ switcher zoom only

Other navigations stay instant. Paired `view-transition-name` on the tapped card and the home content slot creates the zoom illusion.
