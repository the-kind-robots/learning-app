## Why

A phrase's translation is stored as one entry: the line breaks typed with Enter become spaces. A word's translation is one entry a line. The owner wants a phrase to split the same way (#630).

## Changes

- A phrase's translation is read one translation a line, trimmed, blank lines dropped — when added and when edited.
- The German text of a phrase still collapses to single spaces.

## Capabilities

### Modified Capabilities
- `user-phrases`: «Phrase and translation inputs are multi-line», «Phrase translations are edited as a whole string» (renamed), «Adding a phrase creates a phrase document…» — a phrase's translation splits by line.
