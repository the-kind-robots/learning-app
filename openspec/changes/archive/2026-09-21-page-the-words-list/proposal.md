## Why

The words screen renders the whole vocabulary on every render. It used to page — an
HTMX sentinel fetched ten more rows at the bottom (GH-90) — and the Replicant/Nexus
migration (GH-151) dropped the mechanism, leaving only an orphaned CSS rule. On a phone
with a few thousand words every keystroke in the search box rebuilds every row (#317).

## What Changes

- The word list is ordered alphabetically — the owner's decision, and what makes the query
  cheap: the id is the normalised value, so the preview view is already in that order and a
  page is a slice of it. The lesson keeps drawing the most due words (GH-431); the order
  becomes a parameter of the query, named for what the caller wants.
- Retention levels are read only for the rows of the page, by key.
- `:total` reads the view's row count instead of its rows.
- The word list renders a first page of 50 rows instead of the whole vocabulary.
- A sentinel at the end of the list, observed with `IntersectionObserver`, appends the
  next 50 rows when the reader reaches the bottom. No "показать ещё" button — the screen
  loaded on scroll before and keeps doing so.
- Typing in the search box goes back to the first page.
- Editing or deleting a word reloads the list at the row count already on screen, so the
  reader is not thrown back to the top. The reload a sync pull triggers does the same: the
  page stores the query its rows came from, not a bare load effect.
- The sentinel is rendered only while unrendered rows remain.
- The vocabulary use case reports how many rows survived the search filter, so the
  presenter can say whether another page exists. `:total` stays pre-filter (GH-359) and
  keeps telling an empty vocabulary apart from a search with no match.

## Capabilities

### New Capabilities
- `vocabulary-list-paging`: how many rows the word list renders, how the next page is
  asked for, and what resets or preserves the loaded count.

### Modified Capabilities
<!-- None. The empty states of `vocabulary-list-empty-states` are unchanged: an empty
     page and an empty vocabulary are still told apart by the pre-filter total. -->

## Impact

- **Client**: `src/client/use_cases/vocabulary.cljs` (`list` reports `:matches`),
  `src/client/pages/words/presenter.cljs` (page size, `:words/limit`, `:words/more?`),
  `src/client/pages/words/actions.cljs` (`:action/show-more-words`, limits carried through
  the mutation actions), `src/client/pages/words/effects.cljs` (one list call, the
  observer effects), `src/client/pages/words/view.cljs` (sentinel row).
- **CSS**: `.word-list__sentinel` in `resources/public/css/blocks/word-list.css` is in use
  again.
- **Tests**: `test/client/presenter/vocabulary_test.cljs`, `test/client/vocabulary_test.cljs`,
  new `test/browser/words-paging.spec.js`.
