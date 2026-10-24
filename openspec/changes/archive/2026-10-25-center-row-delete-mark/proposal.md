## Why

On a folder row the delete ✕ sits on the count's first line, so under a two-line name it hangs in the row's upper half, and its glyph is off-centre in its own focus ring. The owner asked for the ✕ centred on the row (#632).

## Changes

- A folder row's ✕ changes place. Spec: [collections-navigation](specs/collections-navigation/spec.md).
- The ✕ box grows from 24 to 28 px on every tile, header and row; a focused row takes the editing row's width, so its focus ring has room around the name, the count and the ✕. Layout only, no requirement.

## Capabilities

- Modified: `collections-navigation` — the delete mark's place on a folder row.

## Decisions

- Centred on the row, not on the first line of the name: the owner's choice from the mock (https://claude.ai/artifact/HgcchMceSwyZ8Hdw6ru1aR).
- Only the folder row changes; a tile's and a header's ✕ stay on their count, where the count is already a single line beside or under the name.
