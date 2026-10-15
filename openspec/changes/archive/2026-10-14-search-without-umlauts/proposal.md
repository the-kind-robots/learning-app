## Why

Typing an umlaut on a phone is slow. `fuer` already finds `für`, but `fur`, `schon` and `Madchen` find nothing (#578).

## What Changes

- The dictionary also holds the umlaut-stripped spelling of each form, so home completion finds `für` from `fur`.
- The words filter matches a word's value and translations also by their umlaut-stripped spelling.
- `fuer` keeps working. Lesson grading and word ids are unchanged.

## Capabilities

### Modified Capabilities
- `sqlite-dictionary-worker`: completion of an input typed without umlauts.
- `vocabulary-list-paging`: the words filter on a query typed without umlauts.

## Impact

- `src/shared/utils.cljc`, `src/client/domain/vocabulary.cljs`, `src/client/adapters/learner/snapshot.cljs`, `tools/dictionary/dictionary/materialize.clj`, the committed `resources/dictionary/dictionary.sqlite`.
