# Proposal: lesson-token-info-render

## Why

Clicking an annotated word in the revealed correct answer no longer opens the token-info card, so unknown words cannot be added to the dictionary from a lesson. Regression from the Replicant migration (GH-151): `pages.lesson.view/token-info-dialog` was ported as a function but never wired into the render tree — the click writes `:modal/type :token-info` into state and nothing reads it. GitHub issue: #257.

## What Changes

- Lesson page renders `token-info-dialog` when modal state is `:token-info`, restoring the card (known word / add translation / add to dictionary) on token click.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `lesson`: token-info card SHALL open when the user activates an annotated word in the revealed correct answer; card actions add the word or translation to the vocabulary.

## Impact

- `src/client/pages/lesson/view.cljs` — render the dialog from lesson page state.
- No backend, data-model, or port changes.
