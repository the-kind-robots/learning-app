## Why

On a computer the word list scrolls inside its own 600 px column, so the scrollbar hangs in the
middle of the screen beside the rows. GitHub issue: #621.

## Changes

- The box that scrolls the word list spans the window between the top bar and the search field;
  the rows stay a 600 px column centred in it.
- The box keeps the scrollbar's room on both edges.

## Capabilities

### New Capabilities
- `vocabulary-list-layout`: where the word list's scrollbar stands and what keeps the rows centred.

### Modified Capabilities
- none: `vocabulary-list-paging` measures against "the box that scrolls the list", which is still
  the list's own box.

## Decisions

- A window-wide scroll box, over scrolling the window: a window scrollbar narrows the pinned top
  bar and footer and moves their content off centre (#620), and its coming and going with a search
  would shift the screen sideways.
- `scrollbar-gutter: stable both-edges`, over centring by hand: the rows sit centred under the
  footer whether the scrollbar shows or not.
