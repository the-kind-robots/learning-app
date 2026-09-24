# Design: lesson-ends-on-leaving-route

## Context

Since #480 the corner ✕ runs `:action/close-screen`, which for the lesson
alone went through `:action/cancel-lesson` → `:effect/end-lesson` (`finish!`,
then home). Back leaves `/lesson` through the router without any of that, so
the stored lesson stays. The routes already carry reitit controllers
(`application/routes`), applied by `main` on every match.

## Decisions

- **The route ends the lesson.** `/lesson` gets `:stop` beside its `:start`,
  dispatching `:effect/end-lesson`. Reitit runs `:stop` whenever the match
  leaves the route, so Back, the ✕ and the finish button all pass through it.
  Alternative: intercept Back in the navigation port — rejected, it would be a
  second place that knows the lesson.
- **`:effect/end-lesson` only ends.** It calls `finish!` and no longer
  navigates: the `:stop` runs after the navigation it would otherwise repeat.
- **One close action.** `:action/close-screen` goes home on every screen. The
  finish button dispatches `:action/go-to-home` itself. `:action/cancel-lesson`
  and `:action/finish-lesson` have no caller left and go.

## Risks / Trade-offs

- [A second ending] The finish button used to call `finish!` itself; it no
  longer does, so each leaving calls it once. `finish!` on an absent lesson is
  a no-op in any case (covered by `lesson_test`).
- [Back before the lesson loaded] `:start`'s `restart!` may still be saving
  when `:stop` removes. The next entry's `restart!` removes first, so a stray
  stored lesson never reaches the screen.
