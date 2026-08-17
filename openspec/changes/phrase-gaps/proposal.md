## Why

German pair constructions — `entweder … oder`, `nicht nur … sondern auch`, `je … desto`,
`weder … noch` — are learned as one unit with a gap in the middle, and there is no way to
record that gap (GH-364). Typed as `entweder ... oder` today, the value survives and the
phrase mode is detected, but `sanitize-text` replaces `.` with a space, so `vocab-id`
yields `vocab:entweder oder` — the same id as the ungapped `entweder oder`. The two
documents merge silently, and the ellipsis means nothing to the model beyond three
characters that happened to survive.

## What Changes

- A phrase value MAY carry one gap, written by the user as `...` or `…` and stored as a
  single `…` (U+2026). The gap is part of the value, not a new document field.
- `entweder … oder` and `entweder oder` become distinct documents. This falls out of the
  existing id scheme: U+2026 is outside the ASCII punctuation class `sanitize-text`
  strips, so `normalize-german` passes it through unchanged and the ids already differ.
  **`normalize-german` is not touched** — the ADR-0008 frozen contract holds, no
  existing id moves, no migration.
- A gap renders as a gap. The words list and the lesson footer show the construction with
  a typographic blank in place of the `…`, not three dots.
- Phrase grading treats the gap as a wildcard: the fixed parts must appear in order, and
  whatever the learner puts between them — including nothing — is accepted. This relaxes
  the current "exact typed answer" rule for gapped phrases only.
- No new document field and no change to the document shape, so a client that predates
  this change reads a gapped phrase as an ordinary phrase whose value contains a `…`
  character. Nothing to negotiate across sync.

## Capabilities

### New Capabilities
<!-- None. A gap is a property of an existing phrase, not a new capability. -->

### Modified Capabilities
- `user-phrases`: gains gap entry (input canonicalization, one gap per phrase), gap
  rendering in the words list and the lesson, and the wildcard grading rule for a gapped
  phrase. The existing "exact typed answer" requirement is narrowed to ungapped phrases.
- `data-model`: the phrase-document requirement states that a gap is carried inside
  `value` as U+2026, that no field is added, and that a gapped value is therefore a
  different `_id` from the same value without the gap.

## Impact

- **Client**: `src/client/domain/phrase.cljs` (canonicalize the gap alongside `collapsed`;
  a gap predicate and a segment split for grading), the lesson grading path and its view,
  the words list presenter.
- **Not touched**: `src/shared/utils.cljc` — `normalize-german` and `vocab-id` keep their
  current behaviour, which is what makes the id split free.
- **Sync**: none. Document shape is unchanged; the gap is a character in an existing
  field.
- **Dictionary**: no help available — zero lemmas contain an ellipsis, so gapped
  constructions are hand-entered only. Autocomplete is unaffected.
