## Context

`entered-translation` collapsed a phrase's translation to single spaces and `translation-entry` made one entry of it; a word's went through `parse-translations`, one entry a line. Enter in the translation field already inserts a line break for both kinds.

## Decisions

- A phrase's translation goes through `parse-translations`, the word's path: `entered-translation` and `translation-entry` are gone, `new-phrase` takes the entries.
- A phrase's German value still collapses to single spaces: its line breaks stay visual.
