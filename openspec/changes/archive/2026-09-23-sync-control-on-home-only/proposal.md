## Why

The sync control drew two circular arrows, which reads as "reload the page",
and it stood in the header of every page although only the home page is where
devices get connected.

## What Changes

- The control draws a laptop beside a phone.
- It shows on the home page only, still only once an account exists.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `push-sync`: where the device-sync control is offered and what it shows.

## Impact

- **Client**: `src/client/application.cljs` (icon), `src/client/application/presenter.cljs`
  (`:show-sync?` reads the page), `resources/public/css/blocks/app-shell.css` (comment).
- **Tests**: `test/client/presenter/shell_test.cljs`.
