## Why

Two versions of a word written at the same `modified-at` merged into whichever came later in the resolver's input, so the order of revisions decided the word (#745). A tie is common: the merged word keeps the winner's `modified-at`, so two devices resolving one conflict at once write two revisions with the same time.

## Changes

- A tie on `modified-at` is broken by revision, as collections already do.
- Translations are gathered from the oldest version to the newest, so their order does not depend on the input either.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `data-model`: vocab conflict resolution does not depend on the order of the conflicting revisions.
