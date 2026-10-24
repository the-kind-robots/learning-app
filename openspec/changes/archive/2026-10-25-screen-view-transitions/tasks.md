## 1. Behaviour first

- [x] 1.1 Browser tests for every scenario of `screen-transitions`, phone and desktop, in `test/browser/screen-transitions*.spec.js`; catalog regenerated

## 2. Implementation

- [x] 2.1 `view-transition`: run an update as a transition, or at once where motion is not wanted
- [x] 2.2 `application.presenter/screen-change`: the transition between two states
- [x] 2.3 `render!` renders through it
- [x] 2.4 The deletion of a collection and the screen without it run inside a gap-closing transition
- [x] 2.5 `view-transitions.css`: keyframes per transition, names only under their `data-transition`

## 3. Verify

- [x] 3.1 New and existing browser suites pass; specs that touch a screen right after opening it wait for the change to end
- [x] 3.2 Mid-transition frames at phone width, paused at 140 ms, read as in the canvas
