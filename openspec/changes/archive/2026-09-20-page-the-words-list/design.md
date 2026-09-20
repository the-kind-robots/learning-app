## Context

`pages.words.effects` calls `vocabulary/list-active` in four places with
`{:order :asc :search search}` — never `:limit`, never `:offset`, though the use case has
supported both since it was written. The view then renders every row it is handed.

Two constraints are settled and not reopened here:

- `:total` is deliberately pre-filter (GH-359). The empty vocabulary and the search that
  matched nothing are different screens, and that is how they are told apart.
- The list is sorted by how due each word is — by `:urgency` since GH-431, with the
  retention level the row shows as its image — so the query needs every word's key before
  it can name a first page. GH-404 measured that read (9000 reviews: all rows 1.1 s,
  beating keyed lookup at the sizes that matter) and chose reading all reviews. The win
  claimed here is the render, not the query.

## Goals / Non-Goals

**Goals:**
- A first render that puts one page of rows in the DOM.
- The next page on scroll, with no button.
- A loaded row count that survives an edit or a delete and resets on a search.

**Non-Goals:**
- A lazy query. The sort reads every word's reviews either way.
- Row virtualisation — rows already rendered stay in the DOM.
- Changing what `:total` counts.

## Decisions

**Limit, not offset.** Each load asks for rows `0..limit` with a growing limit rather than
appending a fetched `offset` window to state. The query cost is the same — the sort has
already read every review — and it removes the two failure modes an offset has: a row
inserted or deleted between two fetches shifting the window, and a merge step that has to
decide what to do with rows that changed. The loaded count is then one number in state,
which is also exactly what an edit has to preserve.

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
