## 1. Reproduce

- [x] 1.1 Browser spec: from the server's splash to home, nothing else is shown; fails before the change

## 2. Render once there is a screen

- [x] 2.1 The render function given to the store watch, ahead of the instrumentation, renders nothing until memory is loaded or a read has failed
- [x] 2.2 `.app-loading` is rendered only with the message that asks for a reload; «Загружаем...» there and the initial `:page/loading` are gone
- [x] 2.3 The read at start runs once: a failure marks `:learner/unreadable?`, `start!` resolves nil, nothing reads again
- [x] 2.4 The startup probes in the browser specs assert something that can fail: the first themes tile counts every word, and the words screen never shows an empty state
- [x] 2.5 Unit tests, the console guard and the browser suite pass
