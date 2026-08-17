## Context

A German pair construction is one learnable unit with a hole in it: `entweder … oder`,
`nicht nur … sondern auch`, `je … desto`, `weder … noch`. Nothing in the model can say
"words go here" (GH-364).

Current state, traced:

- `new-phrase` stores `:value` as typed (whitespace collapsed), so an ellipsis is
  displayed back.
- `vocab-id` is `"vocab:" + normalize-german value`, and `sanitize-text` replaces every
  ASCII punctuation character — `.` included — with a space. `entweder ... oder` and
  `entweder oder` therefore normalize to the same string and collide on one document.
- `phrase-value?` is right by accident: three space-separated tokens.
- `normalize-for-grading` strips the dots too, so an answer without the ellipsis passes.

In-force ADRs (0001-0009, 0011 accepted; 0010 and 0009 proposed; no supersessions):

- **0008** freezes `normalize-german` as an id contract. `_id` is permanent, so changing
  normalization remaps every existing word. This design may not touch it.
- **0011** puts phrases in the vocabulary namespace with `:kind "phrase"`, and lists
  "a phrase trial requires exact typing" among the rules keyed on the kind.

## Goals / Non-Goals

**Goals:**

- A gapped construction can be entered, stored and reviewed without colliding with the
  same words ungapped.
- The gap renders as a gap, not as surviving punctuation.
- No change to `normalize-german`, no id migration, no document-shape change.

**Non-Goals:**

- Grading what the learner puts *into* the gap. Nothing is stored about the filler, so
  there is nothing to grade it against.
- A cloze trial type that asks only for the missing words. That needs a stored filler and
  would only ever be self-assessed, which ADR-0011 and the `user-phrases` spec removed.
- Dictionary support. Zero lemmas contain an ellipsis; these are hand-entered only.
- A manual word/phrase/gap toggle in the add form. The form deliberately offers no mode
  control.

## Decisions

### D1 — The gap lives inside `value` as one `…` (U+2026), not in a new field

Alternatives considered:

- **A separate marker field** (`:gaps [1]`, token indices). Rejected: it is a second
  source of truth for something that is meaningless without the value, and it has to be
  kept correct through add, merge, edit, import and conflict resolution. It also changes
  the document shape, which is a cross-device compatibility question rather than a local
  one — a client that predates the field would render the phrase with no gap at all.
- **A sentinel token** (`___`, `<gap>`). Rejected for the same reason as ASCII dots
  below, plus it is not what a user types.

Keeping the gap in `value` changes no field, so an older client reads a gapped phrase as
an ordinary phrase whose text contains a `…`. That is a graceful degradation, not a
break.

### D2 — U+2026 is the stored form because it is the only one the id scheme can see

`sanitize-text` strips the ASCII punctuation class
``[!"#$%&'()*+,\-./:;<=>?@[\]^_`{|}~]``. U+2026 is not in it, and `str/lower-case` leaves
it alone. Measured against the actual normalization:

```
"entweder ... oder"  -> "vocab:entweder oder"
"entweder … oder"    -> "vocab:entweder … oder"
"entweder oder"      -> "vocab:entweder oder"
```

So canonicalizing the typed gap to U+2026 splits the ids **without touching
`normalize-german`**. ADR-0008's frozen contract holds, every existing id is byte-identical,
and there is no migration. This is why the gap is a character and not a field: the
character is already load-bearing in the id, and a field could never be.

What ADR-0008 freezes is the *function*, not the set of ids: a value nobody could enter
before naturally gets an id of its own, which is what content addressing is for. The rule
it protects — that an existing word never remaps — is untouched here, because no value
that is storable today changes its normalization.

The identity split needs no further code: `find-word-by-value` in
`adapters/progress_store.cljs` is a `dbs/get` on `(vocabulary/vocab-id value)`, so
get-or-create already keys on the gap the moment the gap is in the value. The same holds
for the `vocab:` conflict resolver in `sync.cljs`, which merges translations per id.

The canonicalization is a phrase-domain concern, so it belongs beside `collapsed` in
`domain/phrase.cljs`, not in `utils`. It must run before the value reaches both `vocab-id`
and `:value`, so the id and the stored text agree, and before mode detection, so what the
form detects and what it stores agree.

Input accepted: a run of two or more ASCII dots, or a literal `…`. The user never has to
produce U+2026 from a keyboard.

### D3 — A gap at the start or end of the value is dropped

A gap only means something between two fixed parts. At an edge it carries no information
and, under D4, would make grading match almost anything.

### D4 — Grading treats the gap as a wildcard *(owner's call — see Open Questions)*

The reference is split on the gap; the fixed segments must appear in order, anchored at
both ends, with anything or nothing between them. `entweder Kaffee oder Tee`,
`entweder oder` and `entweder … oder` all pass; `sowohl … als auch` does not. Both sides
still go through `normalize-for-grading` first, so all existing typographic forgiveness is
unchanged, and an ungapped phrase grades exactly as it does today.

This narrows ADR-0011's "a phrase trial requires exact typing" to ungapped phrases.
Alternatives are compared under Open Questions.

### D5 — The presenter splits the value; the view renders the blank

The presenter emits the value already segmented, and the view interleaves a gap element
between segments. Views do not test or compare (repo rule); the split is a computation.
This covers both the words list row and the lesson's reference display.

### D6 — Mode detection is untouched

Every construction in scope is three or more tokens, so `phrase-value?` already returns
true and `word-pair?` (exactly two tokens) cannot fire. No change to `phrase-value?`,
`add-mode`, or the autocomplete path.

## Risks / Trade-offs

- **An ellipsis typed as real punctuation (trailing off) becomes a gap** → Accepted. In a
  phrase a user chose to learn, an ellipsis in practice means "something goes here", and
  the alternative is a manual toggle the add form deliberately does not have.
- **Wildcard grading passes a wrong answer that happens to contain both fixed parts** →
  Mitigated by anchoring the match at both ends: only the gap is free, and the fixed parts
  must appear in order. A learner typing a whole unrelated sentence is not the failure mode
  worth designing against.
- **An `entweder ... oder` added before this change already merged into `entweder oder`** →
  Not migrated. After the change a fresh add creates the correct separate document; the
  stale one keeps whatever it accumulated. The `content-addressed-vocab-ids` proposal
  records that there are no users to migrate.
- **Allowing a gap to match empty means `entweder oder` is graded correct** → Accepted:
  the learning target is the pair, not the filler, and demanding invented filler would
  fail a correct recall of the construction. Reversible in one character (`.*` → `.+`).

## Migration Plan

None. No id moves, no field is added, no document is rewritten. The change is live for
values entered after it ships.

## Open Questions

1. **Grading strictness for a gapped phrase — owner's decision.** Three options, in cost
   order:
   - *Wildcard (D4, recommended).* Fixed parts in order, anchored, gap free. The learner
     may fill the gap naturally or skip it. Cost: a segment split and a built regex, plus
     tests. Risk: looser than every other trial type.
   - *Literal.* The reference is `entweder … oder` and the answer must match it after the
     gap is canonicalized on both sides. Cheapest, and consistent with ADR-0011's exact
     typing. Cost: near zero. Risks: it teaches typing an ellipsis, which is not German;
     it fails a learner who writes real filler; and the requirement is invisible — a
     phrase trial shows only the Russian prompt, so nothing on screen tells the learner
     that dots are part of the expected answer. Showing the skeleton as a hint would fix
     that by giving away the whole answer.
   - *Cloze.* Show the skeleton with a blank and ask only for the filler. Rejected under
     Non-Goals: nothing is stored to grade the filler against, so it can only be
     self-assessed, which this codebase removed on purpose. Cost: a new trial type and
     view.
2. **If the wildcard is chosen, ADR-0011 needs a line.** Its "a phrase trial requires
   exact typing" would no longer hold unqualified. The adr step should extend or supersede
   it rather than this design editing it.
