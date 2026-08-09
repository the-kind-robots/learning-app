# Proposal: selectable-answer-tokens

## Why

Annotated words in the revealed answer are buttons; the UA default `user-select: none` on buttons breaks mouse selection at every hinted word, so the sentence cannot be selected or copied whole. GitHub issue: #283.

## What Changes

- Answer tokens become text-selectable (`user-select: text`) while staying clickable.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `lesson`: the revealed answer, including hinted words, SHALL be selectable as continuous text.

## Impact

- `resources/public/css/blocks/lesson.css` — one declaration on `.lesson__answer-token`.
- Browser test asserting cross-token selection.
