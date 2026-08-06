# Tasks

- [x] 1. Replace `nexus.batching/install!` with the depth-counter + dirty-flag scheme owned by `application/install-render!`
- [x] 2. Return `:effect/save` to the plain unbatched signature
- [x] 3. Move the instrumentation render counter from a store watch into the render path
- [x] 4. Node tests: several saves render once, no-save dispatch renders nothing, nested dispatch renders once, external write renders, async continuation renders on its own
- [ ] 5. Confirm the #176 render-count table in the browser via `window.__metrics()` (pends the dev stand)
