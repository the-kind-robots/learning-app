## Why

The word mark in the bar reads «Sprecha» whatever the interface speaks. The owner wants the Russian interface to spell it «Шпреха» (#690).

## Changes

- The word mark reads «Шпреха» while the interface speaks Russian, «Sprecha» in any other language; its accessible name follows it.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `interface-language`: the word mark is spelled in the interface's language.

## Decisions

- Only the word mark changes: the page title, the splash, the install guide and the recovery link keep «Sprecha» — the owner asked for the logo.
- The spelling is a dictionary key, `:shell/word-mark`, like every other text on screen.
