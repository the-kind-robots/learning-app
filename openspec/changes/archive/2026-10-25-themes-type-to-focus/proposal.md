## Why

With many themes, reaching one from the keyboard takes a walk of Tab presses. The owner wants to type a theme's name, or any part of it, and land on it (#636).

## Changes

- Typing on the themes screen moves focus to the theme whose name matches. Spec: [collections-navigation](specs/collections-navigation/spec.md).
- The themes screen listens for keydown on the document while it is mounted: when it opens nothing has focus, so a keystroke reaches no tile.

## Capabilities

- Modified: `collections-navigation` — a new requirement, typing finds a target.

## Decisions

Taken by default, recorded in the issue for the owner to override:

- A substring of the shown name, case ignored; the first match in reading order wins.
- Letters typed within a second of each other make one search; a longer pause starts a new one.
- A space counts only inside a search, so on its own it still activates the focused target.
- Nothing shows the typed text.
