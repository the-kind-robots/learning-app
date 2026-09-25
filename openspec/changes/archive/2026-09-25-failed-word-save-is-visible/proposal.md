## Why

A word whose save failed — storage full, a database that would not open, an
adapter that threw — left the form exactly as it was, with nothing on screen
(#313). The failure was only logged. The one error the form did show, an empty
translation, was a red border with no words.

## What Changes

- A failed save says so under the fields and that it is not the user's fault;
  there is nothing they can do, so the text offers nothing. The typed word and
  translation stay. A save that then succeeds clears the message.
- Every add error carries text, the empty translation included.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `home-add-form-focus`: what the add form shows when a save fails.

## Impact

- **Client**: `src/client/pages/home/effects.cljs` (the catch dispatches the
  error), `presenter.cljs` (error text and the translation flag), `view.cljs`,
  `resources/public/css/blocks/home.css`.
- **Tests**: `test/client/pages/home_test.cljs`, `test/browser/add-word.spec.js`.
