## Why

The shell bar is transparent, so on the themes screen scrolled tiles show through it under the word mark (#712). Each screen worked around it on its own; the bar should solve it once for all.

## Changes

- The shell bar is solid and white, across the full width and of one height on every screen; what scrolls goes under it.
- Its middle becomes a named slot, the info bar, kept for at-a-glance indicators (first planned use: #715). A development build's build mark moves into it.

## Capabilities

### Modified Capabilities
- `app-navigation`: adds the requirement on the solid shell bar.

## Decisions

- One solid bar for every screen, over a per-screen background strip under the bar (the first take of #712) — fixed once, new screens get it for free.
- The bar is always white, over the background of each screen — the owner wants it light everywhere, the grey themes screen included.
- The themes screen keeps scrolling the document, over an inner scroller like the words screen's — its tap recovery reads the document's scroll position.
