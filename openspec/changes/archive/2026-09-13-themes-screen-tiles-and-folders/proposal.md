## Why

The themes screen draws a 9:16 card per collection with fourteen preview rows and a retention dot each, and to draw them it reads every word and every review in user-db (#404 measured the cost). The preview is not what the screen is for: a collection there needs its name, its word count and whether it is active, all of which the collection documents carry.

## What Changes

- The themes screen reads collection documents and the words count only: no `vocab`, no `review` documents.
- Collections render as small tiles in a masonry — name, count, colour accent — two columns on a phone, four from 700 px, alphabetical by rows (locale-aware, case-insensitive). «Всё подряд» is a tile pinned first.
- A `/` in a name makes a folder: the part before the first `/` is the folder key, the rest is a child row on one folder tile. The header is the collection named exactly as the key; its count is the union of its own words and its children's, and tapping a header with no document of its own creates that collection.
- The scope of a collection — what a lesson and the vocabulary list on it cover — is the union of its own `word-ids` and those of every collection named `<its name>/…`. Removing a word from the parent removes it from the parent document only.
- The dashed «new» card is replaced by a floating «+» button.
- **BREAKING** for the preview: card previews, retention dots and the empty-card copy are gone.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `collections-navigation`: tiles in a masonry replace the cards; folders by `/`; floating «+» replaces the create card; the screen reads no vocab or review documents.
- `collections-data-model`: a collection's scope is the union of its own word-ids and its children's by name; the parent is the collection whose name equals the folder key.

## Impact

- Affected specs: `collections-navigation`, `collections-data-model`.
- Affected code: `use-cases.collections` (`summary`, `scope-word-ids`, `create!`), `use-cases.vocabulary/list-active` and through it `use-cases.lesson`, `pages.collections.{presenter,view,actions,effects}`, `resources/public/css/blocks/collections.css`, node tests for the presenter and the scope, `test/browser/collections-loading.spec.js` and a new browser spec for the tiles.
