# 0026. The open windows are a stack

- Status: accepted
- Date: 2026-11-06
- Supersedes: ADR-0015 (in part)

## Context

ADR-0015 keeps the history as home and at most one screen above it: a screen
opened from another takes its entry, and closing any screen returns home. The
statistics (#778) open from today's stars, which the shell shows on home, the
word list and the collections, and the owner wants ✕ and Back on them to
return to the screen they were opened from. Made an exception for one page —
a flag the navigation pushed on and the close read again, in two layers — it
named a position, not a thing. The owner's model names the thing (#789): the
open windows are a stack. The word list opens over home and closes with ✕;
the statistics open over the word list and close with ✕ too.

## Decision

Every window opens over the one on display and closes back to it. Home is at
the bottom of the stack and closes nothing.

- The stack is the application's: `:page/windows`, home first. Opening and
  closing are actions over it; the navigation port only writes the history
  from it (`record!`) and reports the stack of each entry the browser steps
  to (`follow!`). There is no router: the pages declare their paths.
- Opening a page pushes it, unless it is open already beneath: then the
  windows above it come off, so no page is in the stack twice. Going home —
  choosing a collection, the word list's way to add a word — is such a move.
- Closing — ✕, `Escape`, «ЗАКОНЧИТЬ» at the end of a lesson — and the
  browser's Back take the top window off.
- Each history entry holds the stack up to its own, whole (`history.state`):
  Back and Forward read it from the entry, with no pop, and a reload keeps it.
  A close enters the window beneath at once, on screen in the task of the
  tap; the `popstate` that follows brings that same stack and enters nothing.
- A window opened straight by its address gets home beneath it, as before.
- A screen slides deeper when it opens over the one it leaves, and back when
  the one it leaves came off (ADR-0024).

What ADR-0015 says of the port being the history's only writer and of the
corner ✕ still holds; its landing rule becomes the first consequence about
an entry with no stack, below.

## Consequences

- Back no longer always leads home: a lesson started from the word list
  closes to the word list, and only then to home.
- Pages declare nothing about the history; there is no flag to keep in step
  with the close.
- The stack is as deep as the windows opened; no screen opens from the
  statistics, and reopening a page unwinds to it, so it stays shallow.
- An entry with no stack of ours — written before the stack, or an address
  typed in — opens as home with its page over it.
- A move asked for in the few milliseconds between a step back and its
  `popstate` is not queued: the browser keeps the step's target, so the move
  would be lost. Nothing in the app asks for one.
