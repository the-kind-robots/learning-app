## Context

The paged word list landed in #435. `pages.words.effects` holds one `show!` that reads a
page and dispatches `:action/show-words`; five callers reach it — the first render, the
debounced search, load-more, the reload after an edit, the reload after a delete — and
each simply overwrites `:words/*`. Loading is driven by an `IntersectionObserver` on a
keyed sentinel row at the end of the list. The review of that pull request (#439) found
five faults in this driving; the requirements they touch are in
`openspec/specs/vocabulary-list-paging/spec.md`.

ADR-0005 (the client runtime is a system of components) is in force and #418 keeps its
unfinished business: runtime handles belong in the system, not in module atoms. Nothing
here is superseded.

## Goals / Non-Goals

**Goals:**

- The lookahead the code already documents actually happens.
- One rule decides which of several concurrent reads writes the list.
- Loading rows in the background is invisible to a reader editing a word.
- The sentinel observer survives a list rebuild.

**Non-Goals:**

- Moving the observer and the read sequencer into the runtime system (#418).
- The dead paging path and the page number stated twice (#441).
- What paging costs the rest of the session (#440).

## Decisions

**The observer's root is the list node, found from the sentinel.** `rootMargin` widens the
root's rectangle; with no root that rectangle is the viewport, and the intersection is
computed after the ancestors' clip rects have already been applied, so an overflowing
`.vocabulary__list` hid the sentinel before the margin could reach it. The margin was
therefore inert, and its 200px keep their value once the root is the list. The mount hook already receives the sentinel node, so the scroller is
`(.closest node ".vocabulary__list")` — no second query, and no assumption that only one
list exists. Alternative considered: give the list itself a mount hook and keep the
handle by id. Rejected — two hooks to keep in step, for a relationship the DOM already
states.

**Sequencing is a monotonic token, not cancellation.** Each `show!` takes the next number
from a counter and writes only if it is still the last number issued; a read that was
overtaken logs nothing and dispatches nothing. Alternatives: an abort signal through the
worker port (the dictionary port has no cancellation, and a read that has already cost
its query gains nothing by not answering), or per-caller flags like the existing
`loading-more?` (that flag only ever compared load-more against load-more — the fault was
that four other writers were outside it). `loading-more?` stays as it is: it keeps a
re-firing observer from asking the same query twice, which is a cost question, not an
ordering one.

**Closing the dialog belongs to the actions that mean it.** `words-shown` cleared
`:words/editing` on every page of rows, which was invisible while only the reader's own
actions produced rows and became a defect once the observer produced them too. Save and
remove now clear it themselves — remove from its effect, after the confirmation is
accepted, so declining leaves the dialog where it was. The words route also clears it on
entry, which the rows used to do by accident: a reader who leaves the screen with a word
open would otherwise find it open again on the way back.

**The scroll reset rides the row swap.** `:action/show-words` sees both the state's query
and the query the arriving rows were read under; unequal means these rows replace a
different query's rows, which is exactly the moment to scroll to the first row. The
comparison lives in `pages.words.presenter` (`new-query?`) rather than in the action, so
the rule stays with the other derived props. Alternative considered: a mount hook on the
first row. Rejected — appending a page mounts rows too, and the hook cannot tell the two
apart without the query anyway.

**The observer is stored with its node.** One map, `{:node n :observer o}`; the unmount
hook disconnects only when the node it is given is the node in the map. Replicant appends
unmount hooks after mount hooks (`get-hooks-to-call`), so on a rebuild the new observer is
registered first and the old node's unmount would otherwise disconnect it.

## Risks / Trade-offs

- **A discarded read is work already paid for** → The query has run by the time the token
  is checked; only the write is dropped. The alternative is a stale list.
- **Last issued wins, so a reload asked for during a load-more decides the row count** →
  Both are reads of the same list and the later one carries the newer data; a reader who
  loses a page gets it back at the next approach to the end.
- **`.closest` couples the effect to a class name** → The same coupling the scroll effect
  already has, and the browser suite asserts on that class.
