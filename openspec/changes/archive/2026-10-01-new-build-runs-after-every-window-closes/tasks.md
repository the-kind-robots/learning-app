## 1. Release behaviour

- [x] 1.1 Remove the reload on `controllerchange`
- [x] 1.2 Remove «Обновить», `:pwa/new-build-waiting?`, the `:pwa/new-build` component, `:effect/take-new-build` and the control's styles
- [x] 1.3 `sw.js` comments: no skip-waiting request from a release build; only the development bundle sends the message

## 2. Development build mark

- [x] 2.1 The tap posts the activation message and reloads its tab once the worker is activated

## 3. Tests

- [x] 3.1 Browser spec: with a new build waiting under the running one, no page shows a control and no page reloads when DevTools forces it in
- [x] 3.2 Browser spec: after every page of the context closes, a new page runs the new build; a page opened before that runs the old one
- [x] 3.3 Browser spec: «Update on reload» and one reload give exactly one load
- [x] 3.4 Browser spec: the build mark still takes a new build
- [x] 3.5 SW spec, unit tests and the browser suite pass
