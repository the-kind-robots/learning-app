## Why

The word list is ordered by the id a word is stored under, and that id keeps the German
article — `der Zug` is stored as `vocab:der zug`. Every noun is therefore filed under its
article, so the list runs all the `das` words, then all the `der`, then all the `die`, and
the letters the words' own spellings begin with are missing from it (#438).

## What Changes

- The alphabetical word list orders on the word without its article, and still shows the
  word in full.
- The document id does not change. It is the content-addressed identity two devices
  converge on (ADR-0008) and stays exactly as it is; what changes is the key the list is
  ordered and paged by.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `vocabulary-list-paging`: the requirement "The word list is ordered alphabetically" says
  the order is the normalised form the word is stored under. The article falsifies that
  sentence, so the requirement is rewritten.

## Impact

- `adapters.words` — the `vocab-preview` view's map function and its key, so the stored
  design document is replaced and the index rebuilt on the next query.
- `domain.vocabulary` — the key a word is filed under, derived from its id.
- `use-cases.vocabulary` — the three places the alphabetical page is sorted or cut by id.
- No document is rewritten and no migration runs.
