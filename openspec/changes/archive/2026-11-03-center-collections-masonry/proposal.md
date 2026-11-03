## Why

From 700 px the themes screen always lays out four balanced columns. With 1–3 tiles, and with 5, 6 or 9, the right columns stay empty and the tiles hug the left edge of a centred grid (#707).

## Changes

- From 700 px the grid takes only the columns its tiles fill, up to four, and only the width those columns need; it stands in the middle of the page.
- A column keeps the width it has in the four-column grid.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-navigation`: «Collections are tiles in a masonry, one alphabetical sequence down the columns» — column count on desktop and horizontal centring.

## Decisions

- Column count from the tile count in CSS (`:has(> :nth-child(n))`) — no presenter prop for layout, over handing the count down from the presenter.
- The count assumes every tile is one row high. A tall folder tile can still leave a column short; measuring rendered columns in JS is rejected until that case shows up.
