# Proposal: home-lesson-hotkey

## Why

The home screen is where a session starts, and starting one takes a pointer:
`НАЧАТЬ УРОК` sits in the footer, below the add form the user is already typing
in. Adding a word and then starting a lesson means leaving the keyboard. GitHub
issue: #425.

## What Changes

- `Alt`+`Enter` (`Option`+`Enter` on a Mac) on the home screen starts the
  lesson — the same `:action/go-to-lesson` the green button dispatches.
- The handler is page-level, on the home page's root element, so it fires
  wherever the focus sits on that screen: either textarea, a button, the page
  itself. Being on the page's own element scopes it to the home screen; no
  `window` or `document` listener is involved.
- The keystroke's default action is suppressed, so it fires once.
- With an empty vocabulary the hotkey does nothing. The lesson footer is
  `hidden` in that case, and a hotkey must not start a lesson the screen does
  not offer.
- Nothing else on the screen changes meaning: `Enter` on the word field still
  picks the highlighted suggestion or moves to the translation, `Ctrl`/`Cmd`+
  `Enter` on the translation field still submits, `Escape` still dismisses the
  suggestions. `Alt`+`Enter` does not also add a word.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `home-add-form-focus`: gains the home screen's lesson hotkey. That spec
  already owns the screen's keyboard behaviour — `Enter` on the word input,
  `Ctrl`/`Cmd`+`Enter` on the translation field, the focus rings — so the new
  keystroke and its non-interference with them belong beside them.

## Impact

- `src/client/pages/home/view.cljs` — `:keydown` on the page root.
- `src/client/pages/home/presenter.cljs` — the predicate the view consumes.
- `src/client/pages/home/actions.cljs` — the keydown action.
- `src/client/application.cljs` — an `:event.keyboard/alt?` placeholder beside
  the existing modifier placeholders.
