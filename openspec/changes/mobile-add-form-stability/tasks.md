# Tasks

## 1. Instrumentation

- [x] 1.1 Add `:layout-shift` — `:score`, `:input-excluded`, `:entries` — off
      an observer of its own, leaving the standard CLS untouched

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
      renders per keystroke, INP — and the measurements logged
- [x] 4.3 CI: fixture build step, `LEARNING_APP__DICTIONARY_DIR`, path filters
- [x] 4.4 `test/browser/README.md`: fixture, projects, metrics conventions

## 5. The finding

- [x] 5.1 Overlay the suggestion list on phones; in flow it dropped the
      translation field and the submit button 184 px mid-typing
      — **superseded by #373**: in flow under a `176px` ceiling the drop is the
      list's own height (94 px at two rows), bounded at the 180 px the device
      already produced
- [x] 5.2 File the overlay's own cost — it covers the translation field — as
      #349 rather than settling it here
      — dissolved by #373: a list in the flow covers nothing

## 6. Verify

- [x] 6.1 Backend with the variable serves the fixture; without it serves the
      shipped dictionary byte-for-byte as before; pointed at a directory with
      no manifest it answers 404 and still boots
- [x] 6.2 Full suite green; the mobile spec stable over five sequential runs
      after merging master (INP 40-88 ms, shift score 0, submit push 0)
- [x] 6.3 The spec fails on the pre-fix stylesheet — translation field 282 ->
      466, submit push 184, shift 0.044 — so it is a net, not a tautology
- [x] 6.4 CI green on the pull request for this change
