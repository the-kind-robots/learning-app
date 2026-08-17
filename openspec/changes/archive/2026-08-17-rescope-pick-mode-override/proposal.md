## Why

Picking a suggestion sets a mode override that nothing ever clears, so typing on
from the picked lemma keeps the form in the mode the pick chose: `das`, pick
`das Haus`, append ` ist gross`, and a phrase is saved as a word (GH-358).

The override cannot simply be dropped — it is what makes `das heißt` a phrase,
which the space heuristic reads as an article pair — so it has to be scoped
instead of removed.

## What Changes

- A pick's mode applies only while the entered value still is the lemma that was
  picked. Edit the value and the heuristic decides again.
- Sameness is the vocabulary identity already in use: normalized value. Case,
  punctuation and surrounding whitespace do not revoke a pick, because they do
  not make a different entry — `vocab-id` folds them the same way.
- `add-mode` therefore takes the pick as value plus mode instead of a bare mode
  keyword; `:home/mode-override` holds `{:mode _ :value _}`.
- No new control and no other source of override: the form still has no manual
  mode switch, so a pick is the only thing that can override the heuristic.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `user-phrases`: the automatic mode detection requirement gains the scope of a
  pick — the picked mode holds for the picked lemma, and editing the value
  re-runs the detection.

## Impact

- `src/client/domain/phrase.cljs` — `add-mode` signature and rule.
- `src/client/pages/home/actions.cljs` — `select-item` records the picked value;
  `:action/add-word` reads the pick.
- `src/client/pages/home/presenter.cljs` — passes the pick through.
- `test/client/domain/phrase_test.cljs` — override tests.
