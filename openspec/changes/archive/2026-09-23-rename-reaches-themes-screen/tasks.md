# Tasks

## 1. The rename reaches the screen on display

- [x] 1.1 `rename-active!` marks a rename that wrote with `:renamed? true`; a refused one carries
      no mark.
- [x] 1.2 `:effect/rename-active-collection` dispatches `:action/reload-page` after a write, so
      the screen on display reads again; the heading's own text is still set by hand.
- [x] 1.3 Tests: the use case marks a write and only a write; the browser suite renames in the
      heading and clicks the collections icon without leaving it, failing before the fix.
