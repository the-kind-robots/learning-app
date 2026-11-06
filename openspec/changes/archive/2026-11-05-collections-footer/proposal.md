## Why

The collections screen adds a collection with a round «+» floating in the bottom right corner, while every other screen keeps its main button in a footer along the bottom (#773).

## Changes

- The collections screen gets the usual footer, its main button «ДОБАВИТЬ НАБОР» standing where «НАЧАТЬ УРОК» stands on home.
- The floating «+» goes.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-navigation`: how a collection is added — [delta](specs/collections-navigation/spec.md).
- `app-navigation`: the main button keeps its place on the collections screen too — [delta](specs/app-navigation/spec.md).

## Decisions

- The usual footer, not a round button: owner, #773. A first mockup with «НАЧАТЬ УРОК» shrinking into a round «+» was dropped.
- The text says «набор», as the rest of the Russian interface does: owner, #773.
- The footer has the colour every footer has: owner, #773.
