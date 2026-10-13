## RENAMED Requirements

- FROM: `### Requirement: A blank answer is not checked`
- TO: `### Requirement: A blank answer is graded as not recalled`

## MODIFIED Requirements

### Requirement: A blank answer is graded as not recalled
Checking an answer that is empty or only whitespace SHALL grade it as not recalled, as any wrong answer is, on every trial type: the correct answer is revealed and the trial stays in the lesson. A word or phrase trial SHALL record a review as not retained; an example trial records none, as for a wrong answer. The revealed result SHALL NOT show an answer line for the learner when nothing was typed.

#### Scenario: Checking with nothing typed
- **WHEN** the answer field is empty and the user activates «ПРОВЕРИТЬ»
- **THEN** the correct answer is revealed and «ДАЛЕЕ» continues the lesson
- **AND** a review is recorded as not retained
