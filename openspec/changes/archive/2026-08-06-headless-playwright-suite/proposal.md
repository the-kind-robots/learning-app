# Headless Playwright suite

## Why

Browser scenarios were verified by one-off CDP scripts: CSS-class locators that break on markup changes, `setTimeout` waits that are slow or flaky, nothing re-runnable (#169). Headless Chrome already runs inside WSL, so an automated suite needs nothing from Windows.

## What changes

A Playwright suite (`test/browser/`, config at repo root) runs headless inside WSL against the system Chrome (`channel: "chrome"`, no browser download) and a locally started backend — never the shared dev stand. Three specs: adding a word persists it, route entry renders shell and lists, a dialog closes from a backdrop click. Role- and label-based locators with auto-waiting assertions; no sleeps, no CSS-class locators. The CDP skill stays for interactive work with the visible Windows Chrome.

## Impact

- New capability: `browser-test-suite`.
- New: `playwright.config.js`, `test/browser/*.spec.js`, `test/browser/README.md`, `test:browser` npm script; `playwright` + `@playwright/test` devDependencies.
- Out of scope: CI wiring (deferred by #169), service worker, sync/CouchDB.
