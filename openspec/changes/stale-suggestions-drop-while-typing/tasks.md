# Tasks

## 1. The kept list narrows with the typed text

- [x] 1.1 `domain.vocabulary`: one predicate says whether a lemma, normalised and without its article, starts with the normalised typed text
- [x] 1.2 `:action/update-word` keeps only the rows that pass it, in order, with the marked entry kept when it survives; a list with no row left is no list

## 2. Spec and code agree on Enter

- [x] 2.1 The main spec's scenario for Enter without a suggestion list says focus moves to the translation field (synced at archive)

## 3. Verify

- [x] 3.1 Node: a keystroke drops the rows that stopped matching and keeps the rest; umlaut and article do not count as a mismatch; a dropped row is not picked by Enter; a list narrowed to nothing is gone
- [x] 3.2 Browser: `Rücken` settled, then `kurse` at 60 ms per character — the row is gone in the task of the first keystroke, and a click where the last row was leaves the field holding what was typed
