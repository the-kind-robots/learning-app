## Context

`use-cases.collections/summary` reads the collection documents, then `vocabulary/list` over the whole vocabulary (every vocab row plus every review, for retention), and joins words into each collection for the card preview. The screen is scrolled card by card on a phone. Issue #406 fixes the design: tiles, folders by `/`, union scope, floating «+».

In-force ADRs touching this: 0008 (content-addressed vocab ids — `word-ids` hold them), 0012 (dictionary and the visible tab — unrelated here). Nothing constrains the collection document shape; it is `{:id :name :word-ids :created-at}` outward (`adapters.collections`).

## Goals / Non-Goals

**Goals:**
- Opening the themes screen costs a read of the collection documents plus a words count.
- Twelve or more collections visible at 384 × 800.
- One place decides the scope of a collection; the screen's union count and the lesson's word set agree.

**Non-Goals:**
- Folders as documents, nested folders on screen, drag reorder, aggregate progress per collection.

## Decisions

- **Folders are a naming convention, computed on read.** No document changes: `Kurs / Kapitel 1` is a collection like any other, and the presenter groups by the text before the first `/`. Alternative — a `parent-id` on the document — needs a migration and a rename flow; the name already carries the structure the user typed.
- **The parent's scope is the union, computed on read.** `use-cases.collections/scope-word-ids` reads the collections list once and returns the distinct union of the collection's `word-ids` and those of every collection whose name starts with `<name>/` (trimmed, case-insensitive). `vocabulary/list-active` uses it, so the lesson and the words list follow. Adding a word to a child needs no write on the parent. Removing from the parent removes from the parent document only — the child keeps the word, and the union keeps showing it; the alternative (cascade removal into the children) silently edits documents the user did not tap.
- **Name equality is one function.** `create!`'s duplicate check and the parent lookup use the same trimmed, case-insensitive comparison, so the header of a folder resolves to the same document `create!` would refuse to duplicate.
- **`summary` returns neutral collections; the presenter groups.** `{:active-id :items :total-words}` with items as `{:id :name :word-ids :created-at}`. Grouping, sorting, counting and the column placement are pure functions in `pages.collections.presenter`, testable in node.
- **Masonry as flex columns, placement by estimate.** The presenter cannot measure the DOM, so a plain tile weighs 1 and a folder 1 + 0.5 per child row, and each tile goes into the currently lightest column in alphabetical order. The column count lives in state (`:collections/columns`), set on load and on `resize` by one window listener; no per-tile measurement. Alternative — CSS `columns` — orders top to bottom, and the agreed order reads by rows.
- **Sorting with `localeCompare`, `sensitivity: "base"`.** Case-insensitive and locale-aware for German and Russian names; the same comparator for folders among plain tiles and for children within a folder.
- **The words count comes from the vocab view** (`:words/count`, view rows, no document bodies). A reduce on the view would be one row instead of N — deferred until the count shows up in a measurement.

## Risks / Trade-offs

- [Estimated heights differ from rendered ones — a column ends up visibly longer] → the estimate is tuned on the mockup; the order by rows is what matters, and a one-row imbalance is acceptable.
- [A name with `/` that is not meant as a folder] → the row text keeps everything after the first `/`; the tile still reads as the user named it.
- [`remove-from-active!` on a parent leaves the word in the child, so it reappears in the parent's list] → stated in the spec; removing from the child is the way to drop it from the union.

## Migration Plan

No document migration. Existing collections with `/` in their names become folders on the next open of the screen.

## Open Questions

(none)
