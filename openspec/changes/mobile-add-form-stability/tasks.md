# Tasks

## 1. Instrumentation

- [x] 1.1 Count layout shift twice off one observer: keep the CLS-filtered
      `:layout-shift`, add the unfiltered `:layout-shift-all`

## 2. Fixture dictionary

- [x] 2.1 `test/fixtures/dictionary/lemmas.edn`: every prefix of the target
      word matches, and the word itself matches exactly two lemmas
- [x] 2.2 `dictionary.fixture/build` on the production emitters, refusing to
      write into `resources/dictionary`
- [x] 2.3 `:dictionary-fixture` alias in `deps.edn`

## 3. Backend

- [x] 3.1 Serve the dictionary from `LEARNING_APP__DICTIONARY_DIR` when set,
      the classpath resource otherwise, with identical headers and 404s

## 4. Suite

- [x] 4.1 `desktop` and `mobile` projects; `mobile` depends on `desktop` so
      timing is measured on a quiet machine
- [x] 4.2 `add-form-stability.mobile.spec.js`: geometry, unfiltered shift,
      renders per keystroke, slow interactions — and the measurements logged
- [x] 4.3 CI: fixture build step, `LEARNING_APP__DICTIONARY_DIR`, path filters
- [x] 4.4 `test/browser/README.md`: fixture, projects, metrics conventions

## 5. The finding

- [x] 5.1 Overlay the suggestion list on phones; it moved the value field 92 px
      out from under the finger and scored past the shift budget

## 6. Verify

- [x] 6.1 Backend with the variable serves the fixture; without it serves the
      shipped dictionary byte-for-byte as before; pointed at a directory with
      no manifest it answers 404 and still boots
- [x] 6.2 Full suite green; the mobile spec stable over six sequential runs
- [ ] 6.3 CI green on the pull request for this change
