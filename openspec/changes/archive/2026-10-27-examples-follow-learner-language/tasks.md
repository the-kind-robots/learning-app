## 1. Tests first

- [x] 1.1 Browser spec titles in `test/browser/example-language.spec.js`: an English learner's request carries the language; a switch to English asks for an English example of a word that has a Russian one; a switch and back hides and restores the Russian example in the word dialog; `npm run test:catalog`
- [x] 1.2 Backend tests: the checks of an English request, the script of each language, the prompt's name of the language, a request without a language, an unknown language on the GET and the POST, the language in the key, a row's language through the re-key

## 2. Backend

- [x] 2.1 `examples/subject` carries `:language`; the endpoint reads it from the query and the body and answers 400 to one the interface does not speak
- [x] 2.2 The prompt and the checks of the translation and the glosses follow the language; the dictionary's glosses match only a Russian request
- [x] 2.3 The cache key and row carry the language; migration 005 adds the column and marks every row for the re-key

## 3. Client

- [x] 3.1 The request carries the learner's language; the stored example carries it; an example without one is Russian (`domain.examples/in-language`)
- [x] 3.2 The backfill, the lesson and the word dialog see the examples of the learner's language; replacing leaves the others; a change of the language wakes the backfill
- [x] 3.3 The lesson's prompt and hints and the dialog's example translation declare no `lang="ru"` of their own

## 4. Verify

- [x] 4.1 Unit tests, backend tests, the new spec and the example, lesson and interface-language specs, clj-kondo
- [ ] 4.2 One real generation per language against the provider on the dev stand — not provable in the worktree without the provider key
