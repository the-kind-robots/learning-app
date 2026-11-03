## 1. Tests first

- [x] 1.1 `test/browser/interface-language.spec.js`: the word mark reads «Шпреха», then «Sprecha» after English is picked; the word mark's locators match either spelling; `npm run test:catalog`

## 2. Client

- [x] 2.1 `:shell/word-mark` in every dictionary, «Шпреха» in Russian; `:settings/open` in Russian starts with it
- [x] 2.2 `application.view`: the word mark reads the key

## 3. Verify

- [x] 3.1 The interface-language, example-language and words-paging specs, clj-kondo, a screenshot of the bar
