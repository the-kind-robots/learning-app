## Why

The word edit dialog said nothing of the word's example: the learner could not see which sentence a lesson asks, correct it, or ask for another one (#598, owner decisions in #141).

## What Changes

Give the dialog the word's example, to change or to ask anew, and let the lesson and the dialog read the same choice of example. What the dialog and the data then do is in the delta specs of this change (`word-edit-dialog`, `examples-schema`); a later change of this branch, `2026-10-24-word-dialog-cards`, reshapes the dialog.

## Capabilities

### New Capabilities
- `word-edit-dialog`: the dialog's example.

### Modified Capabilities
- `examples-schema`: which example a scope shows, and what replacing one writes.

## Impact

- `src/client/pages/words/`, `src/client/domain/examples.cljs`, `src/client/domain/lesson.cljs`, `src/client/use_cases/examples.cljs`, `src/client/adapters/examples_generator.cljs`, `src/client/adapters/learner.cljs`, `resources/public/css/blocks/word-edit-dialog.css`, `test/browser/word-example.spec.js`.
