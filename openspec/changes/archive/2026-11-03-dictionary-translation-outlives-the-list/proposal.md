## Why

Since #714 the dictionary's translation in the field was read off the open suggestion list. Closing the list — `Escape`, focus moving to the translation field, a tap on the submit button — emptied the field, and the add failed with an empty translation; a keystroke changed it before any answer (#719).

## Changes

- The dictionary's translation is stored in the form again, set only by the dictionary's answer while no translation is the user's.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `home-add-form-focus`: the prefill outlives the list and changes only with an answer.
