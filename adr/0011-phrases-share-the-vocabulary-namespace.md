# 0011. Phrases share the vocabulary namespace and carry their kind

- Status: accepted
- Date: 2026-08-15
- Extends: 0008

## Context

User phrases (GH-285) are authored content with their own spaced-repetition
life. The first design gave them their own document type and id namespace
(`phrase:` + normalized value), which made a phrase and a word with the same
text two separate entries.

Two things argued against it. A document's kind then lives in its id, so it can
never change: the add form guesses word vs phrase from the input, and a wrong
guess could only be undone by deleting the entry and losing its review log.
And the possibility it bought — the same text held twice, once as each kind —
had no use we could name: in a 158k-lemma dictionary only four values exist
both as a phrase and as something else, all interjections.

CouchDB offers no other place to put uniqueness: `_id` is the only unique key,
Mango indexes are secondary and non-unique, and cross-device dedup works
precisely because two devices compute the same `_id` for the same value. So
"one entry per (value, kind)" and "kind is mutable" are exclusive.

## Decision

A phrase is a vocabulary document. `_id = "vocab:" + normalize(value)` as in
ADR-0008, and the kind lives in the document as `:kind "phrase"`. A document
with no `:kind` is a word — which is what everything written before phrases
existed looks like, so nothing has to be migrated.

Domain rules keyed on the kind, not on the namespace:

- a phrase translation is one whole string, never split on punctuation, at add-,
  merge- and edit-time alike;
- a phrase trial requires exact typing and writes to the same review log
  as any other entry;
- no LLM example is requested for a phrase.

Adding a phrase whose value already exists merges translations into that
document and leaves its kind alone: changing what an entry is belongs to the
editor, not to a second add.

## Consequences

- The kind is correctable. Turning a phrase into a word (or back) is a field
  write, not a rebuild — which is the only answer available to a user whom the
  add form guessed wrong.
- One text is one entry. `hätte` cannot be both a word and a phrase.
- Queries, the conflict scan, db routing and import stay single-typed: the
  words list, the lesson pool and the counts read one `vocab:` range.
- `normalize-german` keeps underwriting exactly one id namespace.
