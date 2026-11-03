## Why

The themes screen lists the tiles alphabetically, so the learner cannot put the themes they use most at the top (#709).

## Changes

- A tile is dragged to a new place: a long press that moves, a press that moves on a tile showing its ✕, or a mouse press that moves.
- The order is stored in the collection documents and syncs with them.
- A tile keeps its colour when it moves; a new collection stands last.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-navigation`: the masonry requirement (order and colour), the folder requirement (where a folder stands), a new drag requirement.
- `collections-data-model`: a collection document carries `position`; a merge keeps the winning version's position.

## Decisions

- Owner decisions are on #709: both drag gestures, synced order, colour stays with the tile, whole tiles only, new tiles last.
- Colour follows the order the tiles were made in, not a stored colour: no new field and no merge rule for it. Existing learners see their colours change once.
- A drop writes the position of every tile in the new order (0, 1, 2, …) to the documents whose position changed, over fractional positions between neighbours: tiles without a position get one on the first drop, and there is one rule.
- A mouse press drags past 10 px without a hold: a mouse has no scroll to tell apart.
- The drag is a custom element, `<arrange-panel>`, like the lesson's `<answer-field>`: it knows nothing of the app state, Nexus or the page's CSS. The page marks children with `sort-key` and `armed`; the panel marks them `lifted` and `caret`, and reports `arrange` with the keys in the new order. The page writes the order and renders it; the panel slides the tiles there. Renaming the page's classes cannot break the drag.
- No keyboard reordering; it waits for a request.
