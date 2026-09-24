# 0015. The history is home and at most one screen above it

- Status: accepted
- Date: 2026-09-24

## Context

Home is the app's root, yet Back on it walked through every screen visited
before (#411): each route pushed an entry. A browser can add and replace
entries but cannot delete the ones below the current one, so a history with
nothing behind home has to be kept on every move, not cleaned up later.

## Decision

The navigation port is the only writer of the history, and it follows four
rules:

- home to home: nothing;
- home to a screen: push;
- a screen to a screen: replace;
- a screen to home: `history.back()`, onto the home entry beneath.

The last rule holds only if every screen entry has home beneath it. A screen
reached from home has; a screen the app is opened on directly gets one before
the router starts: when the browser reports the page load's navigation type
as `navigate` and the path is a route other than home, home replaces the
entry and the landing path is pushed over it. A `reload` or `back_forward`
lands on an entry that already has home beneath it and writes nothing.

Links the shell draws take their own click and ask the port; the router's
anchor handler is switched off. Any new screen and any new way home goes
through the port's navigate, never `rfe/navigate`, `rfe/replace-state` or a
bare routed anchor.

## Consequences

- Back from any screen is home; Back from home leaves the app.
- Going home leaves the screen as a forward entry; Forward reopens it.
- A browser session restore that reports `navigate` for a restored screen
  would write a second home entry beneath it. Accepted without having been
  observed.
- Deeper in-app navigation — a screen with its own sub-screen that Back
  should return to — does not fit this shape and would need this decision
  revisited.
