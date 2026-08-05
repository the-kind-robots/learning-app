# In-page measurement behind goog.DEBUG

## Why

Decisions were queuing up that cannot be argued without numbers — #176 rests on render counts per dispatch, #178 on renders per keystroke, #179 on transport overhead — while every measurement lived in ad-hoc scripts under /tmp, rebuilt each session.

## What changes

A dev-only `instrumentation` namespace counts renders (store notifications), dispatches with nesting, frames, layout shifts (input-caused ones filtered), long tasks and slow interactions, exposed as `window.__metrics()` / `window.__metricsReset()`. Installed from the render component only under `goog.DEBUG`; a release build eliminates it.

## Impact

- New: `src/client/instrumentation.cljs`; one guarded call in `main.cljs`.
- Modified capability: `repo-browser-debugging`.
- Phase 2 of #177 — protocol-level traces — rides the Playwright suite (#169).
