# Lesson answer reveal copy shortened

## Why

GH-193 (UI polish batch): the error footer labels the revealed correct answer «Правильный ответ:», which the owner wants shortened to «Правильно:». Three sibling checklist items turned out to be already implemented; this change pins the delivered copy and the verified answer-field behavior in the lesson spec.

## What changes

- `error-footer` in `pages.lesson.view` labels the correct answer «Правильно:» instead of «Правильный ответ:».
- Verified already implemented, no code change: the lesson answer field is a `textarea` with `spellcheck="false"`, `autocorrect="off"`, `autocapitalize="none"`, `autocomplete="off"`, so spellcheck is off and long answers soft-wrap; the home add-word on-mount focus runs through `:effect/mobile-autofocus`, which is gated on `(pointer:fine)` and never fires on mobile.
- No new tests: the repo has no view-layer tests, and a hiccup-walking test for one string literal would start a new test category for a copy tweak.

## Impact

Modified: `lesson`. Files: `src/client/pages/lesson/view.cljs`. Visual check pends the shared dev stand.
