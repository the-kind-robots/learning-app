## Why

Typing `gesondert` showed `sondern`, found through its form `gesondert`, already marked. Enter or Tab, pressed only to leave the field, picked `sondern` and its translation: the learner's word was replaced by a different one (#711).

## Changes

- Before any arrow, the mark sits only on an entry whose lemma the typed text begins. An entry reached only through a word form stays unmarked and fills no translation; an arrow marks it.
- The dictionary's translation in the field is derived by the presenter from that entry, not stored on each answer; the stored translation is only the user's.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `home-add-form-focus`: which entry the list marks on its own.

## Decisions

- Owner chose: a form-only entry is not marked, rather than no entry ever being marked on its own. Prefix completion of a lemma keeps its one-key pick.
