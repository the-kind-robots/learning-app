## Why

A generated example explained its word by repeating it: «Er ist lebensfähig, weil er lebensfähig ist.» The sentence is grammatical and says nothing, and nothing rejected it (#703).

## Changes

- The prompt asks for the target once, in a sentence that gives it a context.
- An example that says a word of its target more often than the target does is rejected and regenerated, and the retry is told the rule in the prompt's words.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `examples`: an example says its target once.

## Decisions

- The count is per word of the target, against how often the target itself has it: `von Zeit zu Zeit` still says `Zeit` twice.
- A word counts as the target when it is spelled as the target's word or is a form of its lemma, by the same dictionary rule that finds the target in the sentence.
