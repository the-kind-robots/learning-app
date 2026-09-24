# Proposal: lesson-ends-on-leaving-route

## Why

The two ways out of a lesson behave differently. The corner «Закрыть» ends the
lesson and goes home; the browser's or the system's Back goes home and leaves
the stored lesson in place. Ending the lesson belongs to leaving its screen,
whatever did the leaving. GitHub issue: #484.

## What Changes

- The `/lesson` route ends the lesson when it is left: a reitit `:stop`
  controller removes the stored lesson, by any way out — Back, the corner
  close, the finish button.
- The corner close is one action for every screen: it goes home. The lesson
  branch in `:action/close-screen` goes, and with it `:action/cancel-lesson`.
- The finish button goes home too; the route's leaving ends the lesson.
  `:action/finish-lesson` goes.
- `:effect/end-lesson` only ends the lesson; it no longer navigates.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `lesson`: leaving a lesson by Back ends it, as finishing and closing do.
- `app-navigation`: closing the lesson no longer has an ending of its own; the
  lesson screen ends the lesson on any leaving.

## Impact

- `src/client/application.cljs` — the `/lesson` route's controller;
  `:action/close-screen`.
- `src/client/pages/lesson/actions.cljs`, `effects.cljs`, `view.cljs`.
- `test/browser/close-and-back.spec.js`.
