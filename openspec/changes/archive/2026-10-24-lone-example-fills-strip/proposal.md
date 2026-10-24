## Why

A lone example card spans the strip only from 700 px up; below it the card stays 290 px, so in a narrow desktop window or on a wide phone it is pressed to the left with a blank band beside it (#620). The same issue: `scrollbar-gutter: stable` on the page reserves a strip on the right of every screen, though only the themes screen scrolls the document, so every screen sits off-center.

## Changes

- A lone card spans the strip at every width, not only from 700 px; several cards keep their width and scroll as before.
- The page no longer reserves a scrollbar strip; a screen whose document scrolls shows its scrollbar as any page does.

## Capabilities

### Modified Capabilities
- `word-edit-dialog`: «The edit dialog writes the word, its translations and an example for every collection of the word» — the card width.

## Decisions

- Drop the gutter reserve rather than reserve it on both edges — `both-edges` centres the content but leaves a blank strip at each side of the footer band.
- The themes screen, the only one whose document scrolls, may shift by half a scrollbar when its tiles outgrow the window — over a strip reserved on every screen for that one.
