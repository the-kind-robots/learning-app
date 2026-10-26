## Why

A lesson on a folder's parent has no example for a word that lies only in a child (#444). The lesson takes its words from the parent and its children (ADR-0013), but its examples only from those made for the parent itself, and none are made for it.

## What Changes

- A lesson on a parent sees the examples of its children too, by the rule the word dialog already follows (#603): a word's example comes from the parent when it has one there, otherwise from the child.

What it must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `lesson`: «Lesson trial generation rules» — which examples a lesson on a parent sees.

## Decisions

- The parent reads its children's examples rather than getting examples of its own: the dialog already shows a child's example in the folder scope, and the lesson and the dialog then agree. The backfill stays as it is.
