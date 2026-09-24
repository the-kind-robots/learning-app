## Why

#411 removed the words screen's visible header, but the empty-state spec still
says the header is hidden on an empty vocabulary and stays on screen under a
filter with no matches.

## What Changes

- The two empty-state requirements name what the screen actually hides or
  keeps: the search bar and the lesson button.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `vocabulary-list-empty-states`: the header drops out of both requirements.

## Impact

- Spec wording only; no code changes.
