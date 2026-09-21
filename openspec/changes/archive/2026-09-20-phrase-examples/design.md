## Context

Examples are generated for words only. `use_cases/phrase.cljs` skips the fetch on purpose,
`domain/lesson.cljs` gives a phrase no example trial, and ADR-0011 records "no LLM example is
requested for a phrase" as one of its domain rules. Three normative statements repeat it
(`user-phrases`, `lesson`, `examples`). Issue #371 reverses the decision; its comment carries the
settled answers this design implements and does not re-litigate.

The backend generator is single-target by construction. `valid-example?`/`example-issue` in
`src/backend/examples.clj` check a lemma against `structure` through
`examples.dictionary/lemma-in-structure?`, which calls `same-lemma?`, which strips a leading
article or `sich` and compares whole strings. Sentence length is a flat 3–12 words. The system
prompt is a flat list of rules with six few-shot cases, grown by accretion.

In-force ADRs read before writing: 0001, 0003–0011, 0013, 0014 (0002 is superseded in part by
0012). Only 0008 (content-addressed vocab ids) and 0011 constrain this work. Nothing here touches
ids; 0011's decision point on examples is the one this change reverses.

## Goals / Non-Goals

**Goals:**

- A phrase gets an example by the same path a word does — add flow, task, endpoint, storage,
  lesson.
- The generator accepts a multi-word target without a second code path.
- One rewritten system prompt covers both target kinds, and the retry messages it is paired with
  use its words.

**Non-Goals:**

- No new capability, no new document type, no migration. The example document shape is unchanged.
- No backfill for phrases already in a vocabulary; they pick up an example by the existing
  cross-collection re-fetch rule.
- No change to `answer-segments` or to how a lesson renders an annotated answer.
- No change to the rate limit or the generation budget beyond the extra demand this creates (#306).

## Decisions

**A phrase is represented in `structure` as one item per word.** Each item carries the whole
phrase as `dictionaryForm` and the same Russian gloss, which is exactly the separable-verb mechanic
the prompt already describes and the model already produces. The alternative — one `structure` item
whose `usedForm` is the whole span — would have broken `add-word-indexes`, which maps a `usedForm`
onto one whitespace-separated token, and `answer-segments`, which keys annotations by a single
`wordIndex`. Choosing the existing mechanic means neither has to change.

**The target check generalises rather than branches.** `lemma-in-structure?` today asks whether any
`structure` item's `dictionaryForm` is the same lemma as the target. For a phrase the same question
is asked of the same field; what has to give is `same-lemma?`, whose article rule
(`has-article?` → require an exact match) fires on `das heißt` and treats it as an article-bearing
noun. The fix is to decide by target shape once, not to add a phrase branch at every call site.

**Sentence length floats with the target.** A six-word phrase cannot fit a 3–12 word sentence with
anything around it. The accepted range becomes the target's own word count up to that count plus
about six, never narrower than today's 3–12, so word behaviour is unchanged by construction. This
bound is a generator tuning constant and stays in code: nobody outside the backend can observe it,
so it is not written as a requirement.

**The prompt is rewritten, not extended.** One target concept ("lemma or phrase"), rules grouped by
what they govern, few-shot cut to one case per distinct shape — noun with article, separable verb,
reflexive, homograph, phrase. Few-shot stays in code beside the prompt rather than moving to a
resource file; it is prompt text, and splitting it costs a lookup for no gain. The retry
`issue-messages` are rewritten in the same pass so a retry reads as a correction to a rule the
model was already given, not as a new instruction.

**The lesson lock stays.** An example trial unlocks when the vocabulary trial with its `word-id`
is answered correctly, whichever kind that trial is. `unlock-example-trials` is currently gated on
`word-trial?`; the gate becomes "not an example trial" — the same predicate the answer path already
needs.

## Risks / Trade-offs

- **The rewritten prompt degrades word examples.** → The verification is a before/after run on a
  fixed list of roughly 12 words and 8 phrases against the live provider, counting first-try
  validation passes. The list is fixed and small because each call costs money.
- **A phrase whose words are not adjacent defeats `add-word-indexes`.** → It already walks forward
  through the sentence and allows gaps between items, so `auf jeden Fall` split by `komme` indexes
  fine; the run is confirmed by a test over the split case rather than assumed.
- **Generation cost grows.** → Every phrase now asks the endpoint once per collection, under the
  same rate limit as words (#306). Accepted by the owner in the issue.
- **`same-lemma?` loosened too far.** → Widening the article rule could let `der Leiter` match
  `die Leiter` again. The homograph few-shot case and the existing article tests stay, and the
  change is expressed as a target-shape decision rather than as a relaxed comparison.

## Migration Plan

None. No stored document changes shape and no existing example is invalidated. Deploy is the
ordinary one; rollback is reverting the branch, after which phrases stop queueing fetches and any
phrase examples already stored simply produce example trials that no longer appear.

## Open Questions

None outstanding. ADR-0011 needs revisiting — its decision point "no LLM example is requested for a
phrase" no longer holds — but the reversal is recorded as an amendment note in that ADR, in the
style ADR-0002 uses for the point ADR-0012 superseded, not as a new ADR: the namespace decision
0011 exists to record is untouched.
