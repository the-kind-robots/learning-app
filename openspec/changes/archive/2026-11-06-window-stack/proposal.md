## Why

The statistics (#778) close back to the screen they were opened from, which ADR-0015's "home and one screen" allowed only as an exception, a page flag read by the open and again by the close. The owner's model removes the exception (#789): the open windows are a stack.

## Changes

- Every window opens over the one on display; ✕, `Escape`, «ЗАКОНЧИТЬ» and Back take it off and show the one beneath. A lesson started from the word list closes to the word list.
- Opening a window open already beneath takes the windows above it off; going home unwinds to it.
- A screen slides deeper as it opens over another and back as it is taken off, wherever it lands.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: the history is the stack of open windows — [delta](specs/app-navigation/spec.md), ADR-0026.
- `screen-transitions`: going back is taking a window off, to any window beneath — [delta](specs/screen-transitions/spec.md).
- `statistics`: they close as every window does — [delta](specs/statistics/spec.md).
- `main-thread-runtime`: a window is entered when the open windows change, not by a router's controller, and the app follows the history last — [delta](specs/main-thread-runtime/spec.md).
- `client-runtime-dependencies`: the history, not a router, is followed after the runtime's dependencies — [delta](specs/client-runtime-dependencies/spec.md).

## Decisions

- The stack, every window alike, no page flag: owner (#789).
- «ЗАКОНЧИТЬ» and `Escape` at the end of a lesson take one window off; choosing a collection and the word list's way to add a word unwind to home; a page open already beneath is unwound to, not pushed twice; a window opened by its address gets home beneath it.
- The stack is the app's state (`:page/windows`), opened and closed by actions; each history entry holds it whole (`history.state`), so Back, Forward and a reload read it from the entry, and the port only writes the history; no router: owner, the simpler of two designs. The window beneath is entered in the task of the tap. ADR-0026, rewritten from the statistics' exception.
