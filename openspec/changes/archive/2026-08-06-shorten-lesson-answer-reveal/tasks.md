# Tasks

- [x] 1. Shorten the error-footer reveal header to «Правильно:» in `src/client/pages/lesson/view.cljs`
- [x] 2. Verify the answer field already disables spellcheck/autocorrect/autocapitalize/autocomplete and soft-wraps as a textarea
- [x] 3. Verify home add-word on-mount focus is already mobile-safe (`:effect/mobile-autofocus` gated on `pointer:fine`)
- [x] 4. `npx shadow-cljs compile app node-test` 0 warnings; `node target/node-tests.js` 0 failures (baseline 158 tests / 357 assertions)
- [ ] 5. Visual check of the shortened reveal copy — pends the shared dev stand
