## Context

Behaviour is in `specs/app-navigation/spec.md` and `specs/lesson/spec.md`;
this file says how it is built.

Every route went through `rfe/navigate`, which pushes, and every link the
shell draws (`/home` on the word mark and the themes ✕, `/collections` on the
grid icon) was taken by reitit's own anchor handler, which pushes too. Only
the end of a lesson replaced its entry (#148). A browser cannot remove
entries below the current one, so "home has nothing behind it" can only be
kept, never restored afterwards.

## Decisions

### The stack is kept at [home, screen]

`ports.navigation` owns every history write. One pure function,
`navigation/move`, decides it from three facts: whether the target is home,
whether the current entry is home, and whether the current entry sits on a
home entry.

| from → to | move |
|---|---|
| home → home | nothing |
| home → screen | push, entry marked |
| screen → screen | replace, mark kept |
| marked screen → home | `history.back()` |
| unmarked screen → home | replace |

The mark is `history.state`, the string `"over-home"`. It survives a reload
and back/forward, which a flag in app state would not: after a reload on
`/words` the app must still know that Back leads home. Reitit writes `nil`
state on its own push and replace, so the port writes the history itself and
then tells reitit the path through the `History` protocol's `-on-navigate`,
the same call reitit's own `push-state` makes.

The unmarked-screen row is a fallback, not a path the invariant produces: an
entry written before this change has no mark.

### A direct landing gets home beneath it

Before `rfe/start!`, a landing on a known non-home path with no mark
rewrites the history to `home, screen(marked)` — `replaceState` to `/home`,
then `pushState` of the landing path — so the address bar still shows the
screen and reitit matches it on start. An unknown path is left to the router,
which already replaces it with home.

### Shell links go through the port

The word mark, the grid icon and the ✕ stay anchors — the href is what a
middle click and a screen reader use — and take the click themselves with
`prevent-default` plus the navigation action. Reitit's anchor handler does
not look at `defaultPrevented`, so `rfe/start!` gets an
`:ignore-anchor-click?` that skips a click a handler already took before
falling back to reitit's own check.

### One close action

The ✕ dispatches `:action/close-screen`. On the lesson it ends the lesson
(`:effect/end-lesson`, which saves the cancellation and then goes home);
elsewhere it goes home. The shell's presenter says which corner control the
page carries — `:corner :collections` on home, `:close` everywhere else — so
the view does not compare pages.

### What leaves

`.vocabulary__back` and the visible `.vocabulary__title` (now visually
hidden, as `.lesson__title` already is); the lesson's `.lesson__cancel`
button beside the progress bar, whose job the corner ✕ takes, so the progress
bar spans the header.

## Risks

- Going home by `history.back()` leaves the screen as a forward entry.
  Forward from home reopens it — a lesson reopened that way starts a new one,
  as the route always did. Nothing in the spec forbids Forward.
- A user who types `/words` into the address bar of an open app tab gets a
  fresh document and so a home entry beneath it, leaving the previous home
  entry two steps back.
