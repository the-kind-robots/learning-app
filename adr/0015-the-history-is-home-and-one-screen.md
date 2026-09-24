# 0015. The history is home and at most one screen above it

- Status: accepted
- Date: 2026-09-24

## Context

Home is the app's root, yet Back on it walked through every screen visited
before (#411): each route pushed an entry. A browser can add and replace
entries but cannot delete the ones below the current one, so a history with
nothing behind home has to be kept on every move, not cleaned up later.

## Decision

The navigation port is the only writer of the history. It keeps it as a home
entry with at most one app screen above it: a screen opened from home is
pushed and marked in `history.state` as standing on home; a screen opened
from a screen replaces it and keeps the mark; going home from a marked screen
is `history.back()`. A direct landing on a screen gets a home entry written
beneath it before the router starts. Links the shell draws take their own
click and ask the port, rather than letting the router's anchor handler push.

Any new screen and any new way home goes through the port's navigate, never
`rfe/navigate`, `rfe/replace-state` or a bare routed anchor.

## Consequences

- Back from any screen is home; Back from home leaves the app.
- Going home leaves the screen as a forward entry; Forward reopens it.
- Deeper in-app navigation — a screen with its own sub-screen that Back
  should return to — does not fit this shape and would need this decision
  revisited.
