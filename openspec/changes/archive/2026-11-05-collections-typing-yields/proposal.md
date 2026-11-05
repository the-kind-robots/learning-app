## Why

Typing on the themes screen searched the tiles even while a menu or a dialog was open over it: the letters typed in a dialog's field were lost, and with the settings menu open a letter focused a tile behind it (#775).

## Changes

- While a menu or a modal dialog is open, typing on the themes screen searches no tile and the keys are theirs.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-navigation`: typing on the themes screen — [delta](specs/collections-navigation/spec.md).

## Decisions

- The guard is the one the screens' hotkeys got in #772: an open popover or modal dialog.
