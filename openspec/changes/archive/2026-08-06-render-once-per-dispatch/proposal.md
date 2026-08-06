# Render once per dispatch without effect batching

## Why

Nexus deprecated effect batching, and we depend on it: `:effect/save` is `^:nexus/batch`, installed explicitly since 2026.06. The deprecation reason is real — a batched effect is pulled out of the queue and executed after everything else, so effects no longer run where they are written, and a state-reading effect in the same dispatch sees pre-save state. Both documented replacements (render from `:after-dispatch`, render lock) lose the zero-render column: today a dispatch that changes nothing renders nothing, and we have many such dispatches (#176).

## What changes

- Batching removed; `:effect/save` returns to a plain per-call `swap!`-merge.
- Render scheduling moves to a dispatch-depth counter with a dirty flag: the store watch renders immediately outside a dispatch and only marks dirty inside one; `:after-dispatch` back at depth zero renders once if anything got dirty. A counter, not a flag, because effects dispatch from inside a dispatch (dialog on-mount) and async continuations arrive as new top-level dispatches.
- Effects now see post-save state mid-dispatch (nexus ADR-01 instant effects) — the reordering hazard this issue names is gone.
- `window.__metrics()` counts renders at the render call, not per store notification, since one dispatch may now notify the store several times while rendering once.

## Impact

- Modified: `src/client/application.cljs` (batching out, `install-render!` in), `src/client/main.cljs` (watch replaced by the install call), `src/client/instrumentation.cljs` (render counter moves into the render path).
- New: `test/client/render_test.cljs` pins the render-count contract in node tests.
- Modified capability: `main-thread-runtime`.
- Browser render counts (the #176 table) still pend verification on the dev stand via `window.__metrics()`.
