## 1. The lookahead

- [x] 1.1 `:effect/observe-words-sentinel` gives the observer the scrolling list as its
  root, found from the sentinel node
- [x] 1.2 The stale comment about `rootMargin` widening the viewport goes with it

## 2. Sequencing the reads

- [x] 2.1 `show!` takes a token from a counter and dispatches only while it is the last
  token issued
- [x] 2.2 The five callers keep their existing shapes; `loading-more?` stays as the
  duplicate-query guard it is

## 3. The edit dialog

- [x] 3.1 `words-shown` stops clearing `:words/editing`
- [x] 3.2 Save clears it from `:action/save-word`; delete clears it from
  `:effect/delete-word`, after the confirmation is accepted
- [x] 3.3 The words route clears it on entry, which the rows used to do by accident

## 4. The observer handle

- [x] 4.1 The handle holds the node beside the observer
- [x] 4.2 The unmount hook disconnects only the observer watching the node it was given

## 5. The scroll reset

- [x] 5.1 `pages.words.presenter/new-query?` says whether arriving rows answer a different
  query than the ones on screen
- [x] 5.2 `:action/show-words` scrolls the list to its first row on that; the reset leaves
  `:action/search-words`

## 6. Verify

- [x] 6.1 Node tests: a page overtaken by a search does not write, no page is asked for
  while a search is pending, rows leave an open word open, save closes it, and the scroll
  reset lands on the row swap rather than the keystroke
- [x] 6.2 Browser: the next page arrives while the sentinel is still off screen, reaching
  the end during the search's wait does not undo the query or the reset, a word stays open
  and keeps what was typed while the next page loads
- [x] 6.3 Each of those three browser assertions fails on the code before this change
- [x] 6.4 The existing paging suite and the rest of the browser suite still pass
