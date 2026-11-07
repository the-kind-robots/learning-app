## Why

The screens' hotkeys (#615, #791) are known only to who read the code. The owner asked for a hint (#793).

## Changes

- A button with a hotkey names its key: its tooltip reads its name and the key («Статистика · Alt+S»), and `aria-keyshortcuts` gives the key to assistive technology.
- A text of the interface can hold another: `[:i18n :key {:param [:i18n :other]}]` reads the other in the same language.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: a button names its hotkey — [delta](specs/app-navigation/spec.md).

## Decisions

- A hint on each button, not a sheet of every key: owner (#793).
- The tooltip is the browser's (`title`); a phone shows none, as it has no hover.
- The tooltip is one text, `:shell/with-hotkey`, of the button's name and its key; the name is a text of its own, read inside it: owner, the simpler of two designs (the other: a ready text per button and language).
- The ✕ of a lesson names no key: `Escape` closes a lesson only once it is over.
