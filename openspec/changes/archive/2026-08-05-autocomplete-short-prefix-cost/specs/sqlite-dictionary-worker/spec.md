## ADDED Requirements

### Requirement: Completion query cost scales with the answer, not the prefix range
The completions SQL SHALL select the ten winning lemmas before joining translations or computing the exact-match flag, so that per-row aggregation work is bounded by the result limit rather than by the number of surface forms matching the prefix.

#### Scenario: Short prefix costs the same order as a long one
- **WHEN** completions run for a one-letter prefix matching tens of thousands of surface forms
- **THEN** translations are aggregated only for the ten returned lemmas
- **AND** the query does not build grouping or concatenation structures over the full prefix range

#### Scenario: Rewritten query returns identical rows
- **WHEN** the rewritten query runs for any prefix
- **THEN** its rows, columns, ordering, and limit match the previous grouping query exactly
- **AND** `has_exact` still reflects whether the lemma has a surface form equal to the normalized input
