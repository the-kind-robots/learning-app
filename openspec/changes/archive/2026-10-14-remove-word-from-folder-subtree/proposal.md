## Why

Removing a word from a folder collection did nothing when the word lived in a child: the list shows the union of the folder and its children, but the removal edited the folder's own document only (#562).

## What Changes

- Removing a word while a collection is active takes it out of that collection and out of every child collection that holds it, the scope the word list shows.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-data-model`: removal from a collection reaches its children.

## Impact

- `src/client/adapters/learner.cljs`, `src/client/domain/collections.cljs`, `test/browser/word-remove-from-folder.spec.js`.
