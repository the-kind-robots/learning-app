## Why

Once an example was checked, the field with the placed tiles was gone: the result replaced it. The learner lost the answer they had built, right when they want to compare it with the correct one (#661).

## Changes

- A checked example keeps its field with the placed tiles, above the result's panel; the bank goes, the tiles no longer move.
- For an example the result writes «Правильно:» under the tiles, without «Ваш ответ:»: the tiles are the learner's answer.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: «Checked example keeps the placed tiles» is added; «Error reveal labels the correct answer with short copy» shows «Ваш ответ:» for words and phrases only.

## Decisions

- The checked field is drawn from the graded answer, cut into tiles by the field's own rule (`answer-field/words-of`), not kept from the live `<answer-field>`: a showing has no identity of its own, and the same example can come straight back, so a kept element would carry old tiles into it.
- The field is a child of the panel, before the current trial's part: the panel's colour starts at the verdict, not at the field.
- «Ваш ответ:» goes for an example: with it, a fifteen-word example on a 360×690 phone pushed the prompt off the screen (#751). To be confirmed by the owner.
