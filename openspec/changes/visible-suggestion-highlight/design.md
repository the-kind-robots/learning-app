# Design: visible-suggestion-highlight

The requirement is in `specs/home-add-form-focus/spec.md`. This note records
only the choices the implementation makes and why.

## Context

The suggestion list already had everything the highlight needs. State carried
both `:suggestions/active-idx` and `:suggestions/active` — the index and the
completion map it names. The presenter handed the view decorated items (each
with a computed `:phrase?`) alongside the undecorated `:active` value, and the
view asked `(= item active)`. A decorated map never equals an undecorated one,
so the test was false for every item on every render, including the first one.
The CSS rule and the `data-active` attribute were in place and correct; nothing
ever satisfied the condition that emits the attribute.

The same miss took the scroll with it: `:effect/scroll-nearest` is handed
`".suggestions [data-active]"`, and `querySelector` returned `nil` because no
element ever had the attribute.

## Goals / Non-Goals

**Goals:**

- The active entry is marked on screen, and the arrows move the mark.
- One source of truth for which entry is active, so the mark cannot drift from
  what `Enter` picks.

**Non-Goals:**

- No change to the keydown handler: the index arithmetic, the edge stops and
  the pick are all correct and stay as they are.
- No new ARIA beyond the `role="option"` already rendered. `aria-activedescendant`
  on a combobox is a separate question and not this defect.

## Decisions

**The predicate is computed in the presenter, per item.** Each item leaves
`suggestion-props` carrying its own `:active?`, derived from its position and
`:suggestions/active-idx`; the view reads the prop and renders the attribute.
This is the repo's standing split — views consume props, presenters compute
predicates — and it is also what makes the defect unrepeatable: there is no
longer a comparison in the view that can be made against the wrong shape.

Comparing by index rather than by value was the alternative left in the view.
It would have worked, but it keeps a comparison in the view and leaves a
second copy of "which one is active" in the props.

**`:suggestions/active` leaves state.** It was the only reader's only source,
and it duplicated `:suggestions/active-idx`. Two representations of one fact
are what let the render disagree with the pick in the first place: the handler
read the index, the view read the value. The index stays; the value goes.

## Risks / Trade-offs

- [The presenter now walks the list twice-over per render — `map-indexed` plus
  the existing per-item decoration] → The list is bounded by what the
  dictionary returns for one prefix (tens of entries at most) and was already
  being walked once per render; this adds an index, not a pass over data of
  any new size.
