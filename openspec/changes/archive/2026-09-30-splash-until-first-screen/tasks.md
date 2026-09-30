## 1. Reproduce

- [x] 1.1 Browser spec: from the server's splash to home, nothing else is shown; fails before the change

## 2. Render once there is a screen

- [x] 2.1 The render function given to the store watch, ahead of the instrumentation, renders nothing until `:learner/readiness` is set
- [x] 2.2 `:effect/memory-loaded-basic` computes the screen from loaded memory before it sets readiness
- [x] 2.3 The `.app-loading` branch, the presenter's readiness check and the initial `:page/loading` are gone; a missing page renders no page content
- [x] 2.4 The startup probes in the browser specs assert something that can fail: the first themes tile counts every word, and the words screen never shows an empty state
- [x] 2.5 Unit tests, the console guard and the browser suite pass
