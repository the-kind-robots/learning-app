## Why

Lesson examples already carry structure data from example generation, but the lesson UI drops that structure before showing the correct answer. That blocks inline annotation of the answer text and prevents later hover/tap actions for translation lookup and add-to-vocabulary.

## What Changes

- Keep example `structure` in lesson example trials.
- Build deterministic answer annotation segments from `answer` plus `structure.wordIndex`.
- Render the correct example answer as segmented tokens in the lesson footer instead of plain text.
- Keep the first slice read-only: no add-to-vocabulary action yet, only structured rendering needed for later UI work.

## Impact

- Affected specs: `specs/lesson/spec.md`, `specs/data-model/spec.md`
- Affected code: lesson domain/presenter/view code and lesson tests
- Validation: client lesson tests plus presenter/domain checks for segment generation
