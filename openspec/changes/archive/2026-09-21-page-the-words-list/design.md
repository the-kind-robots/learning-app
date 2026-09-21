## Context

`pages.words.effects` calls `vocabulary/list-active` in four places with
`{:order :asc :search search}` — never `:limit`, never `:offset`, though the use case has
supported both since it was written. The view then renders every row it is handed.

Two constraints are settled and not reopened here:

- `:total` is deliberately pre-filter (GH-359). The empty vocabulary and the search that
  matched nothing are different screens, and that is how they are told apart.
- The order the list is shown in was the constraint that kept the query full: ranking by
  how due a word is — `:urgency` since GH-431 — needs a key for every word before the first
  row can be named, and GH-404 measured that read and chose reading all reviews. The owner
  has since decided the screen is alphabetical, which removes it: the id is the normalised
  value (ADR-0008), so the preview view is already in that order. The lesson still ranks by
  urgency and still reads everything, and that is deliberate.

## Goals / Non-Goals

**Goals:**
- A first render that puts one page of rows in the DOM.
- The next page on scroll, with no button.
- A loaded row count that survives an edit or a delete and resets on a search.

**Non-Goals:**
- German dictionary collation. The stored key order is the alphabet here — it puts
  `abhaengig` before `abhalten`, where a dictionary would not — and nothing is read or
  computed to improve on it.
- A lazy query for the search. A substring can sit anywhere in a value or a translation,
  so the filter reads every word in scope; only the retention read is cut to the page.
- Row virtualisation — rows already rendered stay in the DOM.
- Changing what `:total` counts. It still counts the scope before the filter; only how it
  is read changes.
- A loading state for the screen switch. Measured at 128–148 ms on 1503 words, the first
  page is there before a reader would see one.

## Decisions

**The order is a parameter named for the caller, not for the mechanism.** `list` takes
`:order :alphabetical` (the screen) or `:order :most-due` (the lesson). Two readers want
two different pages, and they cost differently: alphabetical is the order the ids are
already stored in, so a page reads its own rows; most-due ranks on a key computed per word,
so it reads every word and every review in scope. Naming them `:asc`/`:desc` would have hid
that a caller is choosing a cost as much as an order.

**Limit, not offset.** Each load asks for rows `0..limit` with a growing limit rather than
appending a fetched `offset` window to state. It re-reads the rows already on screen, which
is the page the reader has rather than the vocabulary, and it removes the two failure modes
an offset has: a row inserted or deleted between two fetches shifting the window, and a
merge step that has to decide what to do with rows that changed. The loaded count is then
one number in state, which is also exactly what an edit has to preserve.

**`:total` from the view's row count.** It only ever answered "is the vocabulary empty, or
did the search match nothing", and it was paying a full read of the previews to do it. A
view query carries the whole view's row count whatever `:limit` asked for, so `{:limit 0}`
answers it without a row. In a collection the scope is the membership, so the count is the
length of the id list already in hand.

**The use case reports `:matches`.** "Is there another page?" cannot be answered from
`:total`, which counts before the filter. `list` already knows the filtered candidate
count, so it returns it beside `:total`. Asking for `limit + 1` rows and dropping the
extra would answer the same question by a side effect of the page size, and the presenter
would have to un-know the extra row.

**The presenter decides `:words/more?`.** The view takes a boolean and renders the
sentinel or not. Consistent with `vocabulary-list-empty-states`: comparisons live in the
presenter.

**`IntersectionObserver` on a keyed sentinel.** The sentinel carries a `:replicant/key`, so
appending rows in front of it reuses the same node and the observer survives. A single
observer is held in the effects namespace and disconnected on unmount; an in-flight flag
stops a burst of intersections from stacking loads.

## Risks / Trade-offs

- A short list on a tall screen may not push the sentinel out of view, so the reader must
  scroll for the page after next → `rootMargin` of 200 px, plus the sentinel unmounting
  once the rows run out.
- A reload the reader did not ask for — the one a sync pull triggers through `:page/load`
  — would throw them back to the first page → `:page/load` is rewritten on every render of
  the list and carries that render's limit and query, so the reload asks for the page on
  screen.
