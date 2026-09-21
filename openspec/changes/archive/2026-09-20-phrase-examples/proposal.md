## Why

A phrase gets no example, on the reasoning written into `use_cases/phrase.cljs`: "a phrase is
its own example". That does not hold in use. `entweder … oder` or `das heißt` is a construction,
and what a learner needs is to see it inside a sentence — who says it, to whom, in what register.
The phrase alone gives the shape but not the use, which is what an example is for. Issue #371
settles the reversal with the owner.

## What Changes

- Adding a phrase queues an example fetch exactly as adding a word does. The branch that exists
  only to withhold it goes away; the "translation is never split" rule is untouched.
- A lesson gives a phrase an example trial beside its phrase trial, locked until the phrase trial
  is answered correctly — the same lock words already have.
- Example generation accepts a phrase as its target: the generated sentence carries the whole
  construction, inflected and in German word order, and `structure` represents the phrase as one
  item per word, each carrying the whole phrase as `dictionaryForm` and the same Russian gloss.
  That is the separable-verb mechanic the prompt already uses, so `wordIndex` stays one index per
  word and `domain.lesson/answer-segments` needs no change.
- The example system prompt is rewritten rather than extended, around one target concept (lemma or
  phrase), and the validator's target check and accepted sentence length follow the target instead
  of assuming a single word. Prompt wording and the retry messages say the same thing in the same
  words.

Not breaking: existing word examples, stored example documents and the example document shape are
unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `user-phrases`: adding a phrase queues an example fetch instead of deliberately skipping one.
- `lesson`: a phrase produces an example trial; unlocking is driven by the vocabulary trial of
  either kind, not by the word trial alone.
- `examples`: example-fetch tasks are created for any vocabulary entry, word or phrase; and a new
  requirement states what a generated example must contain when the target is a phrase.

## Impact

- `src/client/use_cases/phrase.cljs` — queue the fetch the way `use_cases/vocabulary.cljs` does.
- `src/client/domain/lesson.cljs` — example trials for phrases; `unlock-example-trials` triggered
  by any vocabulary trial.
- `src/backend/examples.clj` — system prompt rewrite, `issue-messages`, sentence-length bound.
- `src/backend/examples/dictionary.clj` — `lemma-in-structure?` and the form matching under it must
  accept a multi-word target.
- `adr/0011-phrases-share-the-vocabulary-namespace.md` — its "no LLM example is requested for a
  phrase" decision point is reversed; the ADR gets an amendment note, not a restatement.
- Cost: every phrase in a vocabulary now asks the generation endpoint once per collection, under
  the same rate limit as words (#306).
