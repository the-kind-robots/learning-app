# 0029. The open windows stay on the page

- Status: accepted
- Date: 2026-11-21
- Supersedes: ADR-0026 (in part)

## Context

ADR-0026 made the open windows a stack, `:page/windows`, but only the history read it: the shell drew one screen,
the one `:page/current` named, and a close entered the window beneath again through its page's `:open`, which
computed it afresh. The screen on display was thus written twice, the stack first and `:page/current` a write later,
by each page's `:open`; a render between the two saw the new stack with the old screen. Nothing showed it while every
screen beneath was made anew on its return.

Today's stars open the statistics from a lesson (#830), and closing them must give the lesson back as the learner
left it. Its typed answer and placed tiles live only in its answer field's element, and its strip keeps its scroll in
its own: made anew, the lesson loses them. A rule for the lesson alone — keep it while covered, do not compute it
again on its return — was tried and rejected: every such exception is a state to keep in mind.

## Decision

Every open window stays on the page, only the top one is drawn, and the stack alone says which screen is shown.

- The shell draws an element for every open window, home first, keyed by its page (`.app-shell__window`,
  `data-window`), all `hidden` but the top one, and draws them empty.
- Each window's screen is drawn into its own element, a Replicant root of its own (`replicant.dom/render` on it):
  only the top window's, on every render. The windows beneath are neither computed nor compared; their elements, and
  what the learner did in them, stay as they were. Shown again, a window is drawn into its element anew, and
  Replicant changes only what differs from its last drawing there. A window taken off has its drawing ended
  (`replicant.dom/unmount`).
- The settings are drawn into an element of their own after the screen, so their mount hook still runs after the
  screen's and the asking settings keep the focus.
- Opening a page computes its screen anew, its page's `:open`, whether it is new on top or open beneath (going home
  by choosing a collection). Closing the windows above one — ✕, `Escape`, Back — shows it as it was left, computing
  nothing. A window with no drawing yet opens as it comes on top: whether it has one, its element knows
  (`:effect/show-window`), so no state keeps it. A reload brings the whole stack but opens only its top.
- The screen on display is the top of the stack; `:page/current` is gone. The window's screen is computed before the
  stack is saved, so it shows with its screen.
- A page's `:in` and `:out` transitions are its opening and closing; a window opened over it, or taken off over it,
  slides deeper and back.

What ADR-0026 says of the stack, the history and the port still holds; its "a close enters the window beneath" now
means it shows it as it was left.

## Consequences

- Closing a window shows the one beneath as it was left: the lesson's trial, typed answer, placed tiles and strip;
  the word list's query, rows and scroll; home's unfinished form. What it shows of the learner's data is as it was
  too: the word list's memory dots after a lesson started from it, home's offer of a lesson after the list deleted
  every word. Opening the screen again computes them anew.
- No page declares anything to be kept, and no action asks whether its window is covered.
- A render costs what the top window costs, whatever lies beneath. Drawing the whole stack every render was tried
  first: the lesson's render grew with the windows beneath it. Measured on the development build in headless Chrome,
  the CPU slowed 4x, the lesson's renders over ten trials, median ms:

  | Beneath the lesson | one screen drawn (before) | whole stack drawn | top window drawn |
  |---|---|---|---|
  | home | 34 | 20, 38 | 23, 24 |
  | home and the word list, 50 rows | 32 | 26, 50 | 21, 20 |
  | home and the word list, 500 rows | 28 | 80, 165 | 30, 22 |

- The shell's render leaves alone what is drawn into a window's element, because the shell draws that element with
  no children. Replicant's documented contract is only that a render into an element compares with its last render
  there; this is its behaviour beyond that. The statistics spec's return to a lesson, its typed answer kept, fails
  if it changes.
- A rule that keys on a screen's presence in the page must key on the window shown: `vocabulary.css` reads
  `.app-shell__window:not([hidden]) > .vocabulary`.
- Time spent over a lesson counts in the time to answer of the trial left waiting; retention caps it.
