## Why

The sync menu opens as a modal sheet with a «Отмена» row, while the language menu beside it opens as a light menu under the word mark (#732). The two controls sit side by side and should open the same way.

## Changes

- The sync menu becomes a popover under the «Синхронизация» control, drawn like the language menu: the browser opens and closes it.
- The «Отмена» row goes; a press outside, Escape or the control again closes the menu.
- The menu is no longer an app-state dialog: `:sync-open/menu` and its open/close actions go.

## Capabilities

### New Capabilities

### Modified Capabilities
- `push-sync`: adds how the sync menu opens and closes.

## Decisions

- No «Отмена» row, as in the language menu — rejected: keeping it as a fourth way to close.
- The browser owns open/closed (`popover`, `popovertarget`), as for the settings — rejected: keeping `:app/sync-open :sync-open/menu` in state.
- The menu is anchored to the control by CSS anchor positioning, with the language menu's fixed place as fallback — rejected: measuring the control in an effect.
