# Tasks

## 1. Form

- [x] 1.1 `:effect/add-word`: a thrown save dispatches `:action/show-word-error :save-failed`.
- [x] 1.2 Presenter: error text per error and mode; `:translation-invalid?` for the red border.
- [x] 1.3 View and `home.css`: the text under the fields, above the button, announced as an alert.

## 2. Verification

- [x] 2.1 Unit: a rejected save keeps the input and sets the error; the presenter gives its text.
- [x] 2.2 Browser: writes forced to fail show the text and keep the input; a retry clears it.
- [x] 2.3 Screenshot at 390 px.
