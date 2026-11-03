## ADDED Requirements

### Requirement: An example says its target once

A generated sentence SHALL say each word of its target no more often than the target itself says it, so that it gives the word a context rather than explaining it by itself. A word of the sentence SHALL count as a word of the target when it is spelled as that word or is a form of the same dictionary lemma (Requirement: A generated example for a phrase carries the whole construction). An example that says a word of its target more often SHALL be rejected as invalid and regenerated, and the retry SHALL be told the rule in the words the prompt states it in.

#### Scenario: The target in both clauses
- **WHEN** an example is generated for the word "lebensfähig" and the sentence is "Er ist lebensfähig, weil er lebensfähig ist."
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: Another form of the target in the second clause
- **WHEN** an example is generated for the word "explodiert" and the sentence is "Die Bombe explodierte, weil sie explodiert."
- **THEN** the example is rejected as invalid and generation is retried

#### Scenario: A phrase that says a word twice
- **WHEN** an example is generated for the phrase "von Zeit zu Zeit" and the sentence is "Von Zeit zu Zeit besuche ich meine Eltern."
- **THEN** the example is accepted
