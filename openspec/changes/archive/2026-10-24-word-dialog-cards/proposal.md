## Why

The first version of the dialog's example (#598) showed one example at a time behind chips, with a write mode and a heavy footer. The owner approved a lighter design: everything the learner can change is written in place on the sheet.

## What Changes

The dialog is rebuilt on the approved mockup «J»: the word and its translations on the sheet, a card for each collection of the word, a footer of three buttons. What it does is in the delta specs (`word-edit-dialog`, `examples-schema`, `data-model`, `user-phrases`); translations are now kept one to a line.

## Capabilities

### Modified Capabilities
- `word-edit-dialog`, `examples-schema`, `data-model`, `user-phrases`.

## Impact

- `src/client/pages/words/`, `src/client/pages/collections/`, `src/client/domain/vocabulary.cljs`, `src/client/domain/examples.cljs`, `src/client/use_cases/`, `resources/public/css/blocks/`, `test/browser/word-example*.js`, `test/browser/word-translations.spec.js`.
