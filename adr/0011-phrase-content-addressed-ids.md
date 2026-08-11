# 0011. Content-addressed ids extend to phrase documents

- Status: accepted
- Date: 2026-08-11
- Extends: 0008

## Context

User phrases (GH-285) are a first-class replicated doc type: not vocab, not an
example — their own spaced-repetition log. The same cross-device dedup problem
ADR-0008 solved for vocab applies to them: two devices adding the same phrase
must converge to one document.

## Decision

Phrase docs are content-addressed in their own namespace:
`_id = "phrase:" + normalize(value)`, with the same frozen `normalize-german`
contract as vocab ids. Spaces survive normalization, so the id is the whole
normalized phrase (`phrase:auf jeden fall`).

Conflict resolution reuses the vocab strategy — LWW scalars, translations
unioned across revisions — with one addition: phrase translation entries are
whole strings and are never split on punctuation, at add-, merge- and
edit-time alike.

Phrase text is immutable like word values: editing is translation-only, a text
change is delete-and-re-add.

## Consequences

- The conflict scan reads two key ranges (`vocab:`, `phrase:`) instead of one.
- `normalize-german` now underwrites two id namespaces; changing it means
  migrating both.
- A phrase and a word can share a normalized value (`vocab:auf jeden fall`,
  `phrase:auf jeden fall`) — the namespaces keep them distinct, and the add
  form warns about the cross-type duplicate without blocking it.
