## Why

Some words and phrases never get an example: every candidate the model writes is rejected by the validator, so the client gets "No example could be generated" (#555). The candidates are correct German; the validator asks for what a natural sentence cannot give — the placeholder of a phrase repeated as a word, the article of the listing, the `structure` annotation in perfect order.

## What Changes

- A placeholder of a phrase (`etwas`, `jemanden`, `jemandem`, `jemandes`, `sich`, `seine`…, and their abbreviations) is a slot: the sentence fills it with anything that fits, and `sich` may be any reflexive pronoun. Articles of a phrase are not required as listed. Every other word is still required.
- A single word is present when the sentence says it in the form it is written in, as well as when `structure` names its lemma.
- A `structure` item that cannot be placed — out of order, spelled as the lemma, naming a word the sentence lacks, glossed in another language, or two words in one — costs its tooltip, not the example.
- The sentence ceiling grows, a time such as `20:00` is allowed in a translation, and a retry is asked at a higher temperature so it does not repeat the refused sentence.

## Capabilities

### New Capabilities

### Modified Capabilities
- `examples`: the requirement on what a phrase's example carries now says what a phrase's placeholders and articles, a single word's surface form and `structure`'s imperfections do and do not cost.

## Impact

- `src/backend/examples.clj`, `test/backend/examples_test.clj`.
