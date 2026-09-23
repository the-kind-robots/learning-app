## Context

The tap recovery (#404) decided at `pointercancel`. Measured in desktop Chrome through `Input.dispatchTouchEvent` at 384 × 800: the cancel arrives as the scroll starts, with `scrollTop` still 0, and the touch events go on after it — `touchmove` with the page scrolling, then `touchend`. Desktop Chrome sends the slop moves as `pointermove` before the cancel; Android does not, which is why the phone saw a travel of 0 at the cancel. On this screen the document is what scrolls (`document.scrollingElement`).

## Decisions

- **Judge at the lift, not at the cancel.** After a touch `pointercancel` the gesture keeps its window listeners and follows `touchmove`; `touchend` makes the decision with the whole travel and the scroll since pointerdown. `touchcancel` ends the gesture with nothing. A non-touch cancel ends it at once — no lift follows it. Alternative rejected: delaying the decision at `pointercancel` by a timer — it guesses how long a scroll takes to show, and still misses the slow start.
- **The long press survives a cancel.** The timer is cleared by travel over the limit, from `pointermove` or `touchmove`, or by the end of the touch — not by the cancel itself, since a still finger Chrome cancels is still a long press.
- **✕ over the count.** The count stays in layout with `visibility: hidden`, so the name's box never changes. Where the count shares the name's line (folder header, row) it reserves 24 px, the ✕'s width, all the time. Rejected: shrinking the name while editing — it reflows the text the user is looking at.
- **Hyphenation from the browser.** `lang` on the name span and `hyphens: auto`; `overflow-wrap: anywhere` stays as the fallback — Chrome takes hyphenation points first. The presenter says which targets carry a language. Rejected: soft hyphens computed in the app — it ships a dictionary for what the browser already has.

## Risks / Trade-offs

- [Chrome on Linux hyphenates only with its downloaded hyphenation data] → the browser spec borrows the local Chrome profile's copy and skips where there is none (CI). Android Chrome ships the data.
- [The phone's pointer stream is emulated] → the browser spec stops `pointermove` at the window to reproduce Android's slop suppression, and asserts a real `pointercancel` happened. A real phone is the owner's check.
