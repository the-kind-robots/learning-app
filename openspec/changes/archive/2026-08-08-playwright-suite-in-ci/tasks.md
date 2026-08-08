# Tasks

## 1. Workflow

- [x] 1.1 Add `browser` output to the `changes` job (paths: app sources plus
      `test/browser/`, `playwright.config.js`)
- [x] 1.2 Add `Browser Tests` job: java/clojure/node setup, m2+gitlibs cache,
      `npm ci`, `npx shadow-cljs compile app`, backend on 8301, curl wait,
      `npm run test:browser`
- [x] 1.3 Upload `test-results/` and backend log as artifact on failure

## 2. Docs

- [x] 2.1 Update `test/browser/README.md`: CI section, drop the deferred note

## 3. Verify

- [x] 3.1 Job goes green on the pull request for this change
