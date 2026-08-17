## Why

Searching the word list for something that is not there renders the first-run
empty state: "Слов пока нет" plus an invitation to add a first word, with the
header, search box and lesson button gone. The vocabulary is full — only the
filter matched nothing — so the message is wrong and the query cannot be edited
or cleared without leaving the page (#359).

## What Changes

- The word list distinguishes an empty vocabulary from a filter that matched
  nothing, and says which one it is.
- With a filter active, the header, search box and footer stay on screen, so the
  query can be corrected or cleared in place.
- The first-run state keeps its current look and CTA when the vocabulary really
  is empty.
- The decision is made in `pages.words.presenter` and reaches the view as ready
  props; the view stops comparing `items`, `search` and emptiness itself.

## Capabilities

### New Capabilities

- `vocabulary-list-empty-states`: what the word list shows when it has no rows to
  render — first-run invitation versus filter-with-no-matches — and which page
  chrome survives in each case.

### Modified Capabilities

None. No existing spec describes the word list screen.

## Impact

- `src/client/pages/words/presenter.cljs` — new empty-state props.
- `src/client/pages/words/actions.cljs` — stores the presented props in state.
- `src/client/pages/words/view.cljs` — consumes them; loses its own predicates.
- `test/client/presenter/vocabulary_test.cljs` — cases for the three outcomes.
- No data model, storage or backend change.
