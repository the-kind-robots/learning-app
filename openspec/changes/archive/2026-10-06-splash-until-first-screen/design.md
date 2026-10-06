## Context

The behaviour this change must produce is in the delta specs beside this file; `learner-data-memory` holds the rule, and `main-thread-runtime` links to it. The owner's decision is on #515: while memory loads, only the server's splash is on display. «Обновить» and the shell appear with the first screen. A failed read at start is the one exception: the shell then says so in place of the splash and asks for a reload. The owner decided on #516 that nothing retries it: no such failure has been observed, and a reload starts the read anew.

## Goals / Non-Goals

**Goals:** the server's splash, then the first screen, and nothing in between.

**Non-Goals:** restyling the splash; showing the failed-read message inside the server's splash.

## Decisions

**The render function given to the store watch does nothing until `:learner/loaded?` or `:learner/unreadable?` is set.** The check sits in `main.cljs`, where the watch is wired, ahead of the development build's instrumentation, so a store write before it is neither rendered nor counted as a render. `install-render!` and `application/render!` are as they were. Store writes before it still reach the watch, and the first render draws the state as it stands then.

**The read at start runs once.** `read-memory` in the loader reads user-db with `load-once` and no longer through `db.pouch/retried`; `retried` stays, since moving device examples still uses it. A failure is logged, the store is marked `:learner/unreadable?`, and `start!` resolves nil without handing memory over or following the change feed.

**`:effect/memory-loaded` keeps its order.** It sets memory and `:learner/loaded?` in one write and then computes the screen again (`:action/refresh-page`). The write renders the screen from the slice its route entry computed from empty memory, and the refresh renders it from memory. Both renders run in one task, so only the second reaches a paint. Computing the screen before `:learner/loaded?` is set would save that render, but `:action/open-lesson` draws a lesson only once `:learner/loaded?` is set, so the lesson would not be drawn.

**The shell keeps its default branch.** The route entry normally sets `:page/current` before memory is read. If the router fails to start, memory still arrives with no page set; the shell's `case` and the presenter's corner then render no page and no corner control instead of throwing on every render.

What rendered early before this change, and what it does now:

- Install guide, update control, build mark: state written before the first render (`:pwa/install-available?`, `:pwa/new-build-waiting?`) is drawn by it.
- Pairing and sync-menu dialogs: opened only from controls on a screen, so never before the first render.
- Status region: `:effect/announce` is dispatched only from screen actions, and it finds the region by id when it runs.
- `guard-double-clicks!`: a window listener that reads `:page/current`; it needs no DOM.
- `sync-virtual-keyboard!`: called at boot, on `pageshow` and after each render; before the first render it finds no overlay, as before.

## Risks / Trade-offs

- [A failure a retry would have outlived, such as a briefly locked database] → the learner reloads the page. Kept until such a failure is seen.
- [The read neither finishes nor fails] → the server's splash stays. Before this change the shell's unstyled «Загружаем...» stayed in the same case.
