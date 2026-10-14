## Why

The word edit dialog shows the German text as a label. A typo cannot be fixed, only deleted and retyped, which loses the word's themes (#575).

## What Changes

- The dialog's word text is editable. A change that keeps the vocab id is an ordinary edit; any other change saves a new word with the same translation in the same themes and deletes the old one as a delete does, in one write.
- A spelling that is already a word is refused in the dialog.
- The dialog warns before saving a changed spelling.
- The delete button reads «Удалить слово».

## Capabilities

### New Capabilities

### Modified Capabilities
- `user-phrases`: the German text is no longer immutable; the dialog's labels.
- `learner-data-memory`: renaming a word is one write.

## Impact

- `src/client/pages/words/`, `src/client/use_cases/vocabulary.cljs`, `src/client/adapters/learner.cljs`, `src/client/ports/learner.cljs`, `resources/public/css/blocks/word-edit-dialog.css`, `adr/0008-content-addressed-vocab-ids.md`, `test/browser/word-rename.spec.js`, `test/client/learner_test.cljs`.
