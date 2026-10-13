## Why

`Alt`+`Enter` on home starts the lesson only while focus is inside the page; with nothing focused the keystroke's target is `body` and it does nothing (#557).

## What Changes

- The home screen's lesson hotkey works wherever the focus sits, including nowhere.

## Capabilities

### New Capabilities

### Modified Capabilities
- `home-add-form-focus`: the lesson hotkey no longer depends on focus being inside the home page.

## Impact

- `src/client/application.cljs`, `src/client/main.cljs`, `src/client/pages/home/{actions,view}.cljs`, and `test/browser/home-lesson-hotkey.spec.js`.
