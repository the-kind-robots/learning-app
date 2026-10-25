## 1. Tests first

- [x] 1.1 Browser spec titles for picking a language, the German first run, a pick arriving by replication and the server's `lang` in `test/browser/interface-language.spec.js`; `npm run test:catalog`
- [x] 1.2 The suite's browser speaks Russian (`locale` in `playwright.config.js`), so the other specs keep their Russian
- [x] 1.3 The snapshot's format test holds a settings document (`format-version` 5)

## 2. The language

- [x] 2.1 `i18n`: the four languages, the first-run rule, a dictionary per language with Russian under a missing one, `interpolate` of a given language
- [x] 2.2 The settings document type, its place in memory, the learner port's language; `use-cases.settings/choose-language!` writes it
- [x] 2.3 `render!` interpolates in the learner's language and sets `<html lang>`
- [x] 2.4 The server declares the language of `Accept-Language`

## 3. The screen

- [x] 3.1 The word mark opens the settings, a popover menu under it with the language list (design variant 5)
- [x] 3.2 The German first run opens it with English marked, closed only by a pick

## 4. Verify

- [x] 4.1 Unit tests, the interface-language, sync-control, words-paging, close-and-back, dialog-backdrop, memory-snapshot and startup-splash specs, clj-kondo, adapter layering
- [ ] 4.2 Two-device replication of a pick on the dev stand — not proven in the worktree; the spec proves a settings document written to user-db re-renders the interface
