## 1. Tests first

- [x] 1.1 Browser tests in `test/browser/sync-control.spec.js` for opening under the control, closing (outside, Escape, control again) and an item closing the menu; `npm run test:catalog`

## 2. Menu as a popover

- [x] 2.1 `application.sync.view`: the menu is a `popover="auto"` under the control, the control its `popovertarget`; items hide it; no «Отмена»
- [x] 2.2 Drop `:sync-open/menu`, `:action/open-sync-menu`, `:action/close-sync-menu` and the presenter's `:dialog/menu`
- [x] 2.3 CSS: the menu drawn like `.settings-menu`, anchored under the control

## 3. Verify

- [x] 3.1 Sync browser tests and the unit suite pass; screenshots of the menu open
