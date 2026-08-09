## 1. Popover mechanics

- [x] 1.1 Port `popover.js` positioning + auto-close into `pages.lesson.popover` (cljs): position at anchor rect, flip below when no room, arrow offset, timers (data-dismiss / idle / leave), resize+scroll reposition
- [x] 1.2 Lesson view: replace `<dialog>` with `#popover` shell (Popover API) containing the token card and arrow; `aria-expanded` on the active token

## 2. Wiring

- [x] 2.1 Effects: open popover after render, reposition after add-token content swap, dispatch close-modal on popover close

## 3. Verify

- [x] 3.1 Update view unit test (popover shell instead of dialog)
- [x] 3.2 Browser check with geometry assertions: popover anchored to token, no centered dialog, auto-close works
