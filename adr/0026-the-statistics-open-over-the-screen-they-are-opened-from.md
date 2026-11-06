# 0026. The statistics open over the screen they are opened from

- Status: accepted
- Date: 2026-11-06
- Supersedes: ADR-0015 (in part)

## Context

ADR-0015 keeps the history as home and at most one screen above it: a screen
opened from another screen takes its entry, and closing any screen returns
home. The statistics (#778) open from today's stars, which the shell shows on
home, the word list and the collections. The owner wants Back and ✕ on the
statistics to return to the screen they were opened from, not to home.

## Decision

A page that declares itself `:over-screens?` (`application.pages/page`) — the statistics — is pushed
over whatever screen is shown: home, the word list or the collections. Closing
it by ✕ or `Escape` steps back in the browser history (`ports.navigation/back`),
as the browser's Back does, so the browser returns the entry beneath and the
router enters it with the `popstate`. Every other rule of ADR-0015 holds: the
statistics opened directly get a home entry beneath them, any other screen
replaces the screen it is opened from, and closing any other screen returns
home.

The navigator does not remember which screen lies beneath the statistics; the
history does. A reload on the statistics keeps it.

## Consequences

- The history can hold home, a screen and the statistics: Back from the
  statistics opened from the word list is the word list, and Back again is
  home.
- The screen beneath appears with the `popstate`, a task after the tap, not in
  the tap's own task as home does when a screen closes to it.
- Leaving the statistics always moves as going back (`:transitions {:out
  :transition/back}`), whichever screen is beneath.
- No screen can be opened from the statistics: they show no stars button and
  no link, so a third level never forms.
