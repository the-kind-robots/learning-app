# Proposal: visible-suggestion-highlight

## Why

On the home add form the suggestion list never shows which entry is active.
`ArrowDown`/`ArrowUp` move the active index in state and `Enter` picks the
entry that index names, but no row on screen is marked, so the user presses
the arrows blind and finds out what they chose only after `Enter`. GitHub
issue: #412.

## What Changes

- The suggestion list marks exactly one row as active, and the arrows move
  that mark on screen: the row the state calls active is the row the eye sees
  highlighted.
- The first row is active as soon as a list appears — that is what `Enter`
  already picks without any arrow press, and until now nothing said so.
- The active row is scrolled into view, so a list taller than its box follows
  the arrows instead of leaving the selection outside the visible rows.
- No keyboard behaviour changes: `ArrowDown` still stops at the last entry,
  `ArrowUp` at the first, `Enter`/`Tab` still pick the active entry, `Escape`
  still dismisses the list. Only what the screen shows changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `home-add-form-focus`: gains the visible highlight. The spec already owns
  the suggestion list's keyboard behaviour and says `Enter` picks "the
  highlighted suggestion" — it never said anything makes a suggestion look
  highlighted. That gap is the defect, so the requirement belongs here.

## Impact

- `src/client/pages/home/presenter.cljs` — each item carries its own
  `:active?`, computed from the active index.
- `src/client/pages/home/view.cljs` — the item renders `data-active` from that
  prop instead of comparing values.
- `src/client/pages/home/actions.cljs` — the redundant `:suggestions/active`
  value in state, which nothing reads once the index is the single source.
- `test/client/pages/home_test.cljs`, `test/browser/` — the highlight under
  the arrows.
