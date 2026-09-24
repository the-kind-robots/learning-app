## Why

Home is the root of the app, yet Back on it returns to whatever screen was
opened before it — words, themes, a finished lesson — and every route there
pushed a new entry, so the history kept growing. The screens also leave in
three different ways: the themes screen has a corner ✕, the words screen a
«← Назад» button under its heading, the lesson a ✕ of its own left of the
progress bar (#411).

## What Changes

- Every screen but home carries the corner ✕ the themes screen already has;
  home carries none.
- The words screen loses its back button and its visible heading.
- The lesson's own ✕ gives way to the corner ✕, which closes the lesson as
  that ✕ did.
- The history holds home and at most one screen above it: a screen opened
  from home is pushed, a screen opened from another screen replaces it, and
  going home steps back to the home entry below. A screen landed on directly
  gets a home entry put beneath it.

## Capabilities

### New Capabilities

- `app-navigation`: how a screen is closed and what the browser's Back does.

### Modified Capabilities

- `lesson`: leaving a lesson follows the app's navigation rules instead of
  replacing the history entry itself.

## Impact

- **Client**: `src/client/ports/navigation.cljs` (history policy),
  `src/client/main.cljs` (home entry under a direct landing, anchor clicks
  a handler took), `src/client/application.cljs` and its presenter (corner
  control per screen, close action), `src/client/pages/words/view.cljs`,
  `src/client/pages/lesson/view.cljs`, `src/client/pages/lesson/effects.cljs`.
- **CSS**: `vocabulary.css`, `lesson.css`.
- **Tests**: unit test of the history policy, shell presenter test, a
  Playwright spec over the screens and the Back button.
