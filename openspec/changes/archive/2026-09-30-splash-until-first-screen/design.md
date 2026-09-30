## Context

The behaviour this change must produce is in the delta specs beside this file; `learner-data-memory` holds the rule, and `main-thread-runtime` links to it. The owner's decision is on #515: while memory loads, only the server's splash is on display. «Обновить» and the shell appear with the first screen.

## Goals / Non-Goals

**Goals:** the server's splash, then the first screen, and nothing in between.

**Non-Goals:** restyling the splash; showing the reviews earlier; a fallback for a load that never finishes.

## Decisions

**The render function given to the store watch does nothing until `:learner/readiness` is set.** The check sits in `main.cljs`, where the watch is wired, ahead of the development build's instrumentation, so a store write before readiness is neither rendered nor counted as a render. `install-render!` and `application/render!` are as they were. Store writes before readiness still reach the watch, and the first render draws the state as it stands then.

**The first render is drawn from loaded memory.** `:effect/memory-loaded-basic` takes the words and collections into memory, computes the screen on display again (`:action/refresh-page`), and only then sets readiness. So the first render is one render, of the screen with its data. Setting readiness first would draw the screen from the slice its route entry computed at boot, from empty memory, and then draw it again.

**The shell keeps its default branches.** The route entry normally sets `:page/current` before memory is read. If the router fails to start — a malformed address makes it throw — readiness still arrives with no page set. The shell's `case` and the presenter's corner then render no page and no corner control instead of throwing on every render.

What rendered early before this change, and what it does now:

- Install guide, update control, build mark: state written before readiness (`:pwa/install-available?`, `:pwa/new-build-waiting?`) is drawn by the first render.
- Pairing and sync-menu dialogs: opened only from controls on a screen, so never before the first render.
- Status region: `:effect/announce` is dispatched only from screen actions, and it finds the region by id when it runs.
- `guard-double-clicks!`: a window listener that reads `:page/current`; it needs no DOM.
- `sync-virtual-keyboard!`: called at boot, on `pageshow` and after each render; before the first render it finds no overlay, as before.

## Risks / Trade-offs

- [The load does not finish] → the server's splash stays. The loader retries the reads of the words and collections after a failure, waiting longer each time. It does not retry the reads of the databases' update sequences that come before them; a failure there stops the loader, and the splash stays for the life of the page. Before this change the shell's unstyled «Загружаем...» stayed in the same case.
