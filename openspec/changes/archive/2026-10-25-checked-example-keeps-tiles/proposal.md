## Why

Once an example was checked, the field with the placed tiles was gone: the result replaced it. The learner lost the answer they had built, right when they want to compare it with the correct one (#661).

## Changes

- A checked example keeps its field with the placed tiles, above the result; the bank goes, the tiles no longer move.
- The result still writes «Ваш ответ:» above «Правильно:».

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: «Checked example keeps the placed tiles» is added.

## Decisions

- The checked field is drawn from the graded answer, not the live `<answer-field>`: a showing has no identity of its own, and the same example can come straight back, so a kept element would carry old tiles into it.
- «Ваш ответ:» stays in the result beside the tiles — the owner asked for the wrong answer written above the right one.
