# Tasks

## 1. The route ends the lesson

- [x] 1.1 `:stop` on the `/lesson` route dispatching `:effect/end-lesson`
- [x] 1.2 `:effect/end-lesson` calls `finish!` only, no navigation
- [x] 1.3 `:action/close-screen` goes home on every screen
- [x] 1.4 The finish button dispatches `:action/go-to-home`; drop
      `:action/cancel-lesson` and `:action/finish-lesson`

## 2. Verify

- [x] 2.1 Browser: answer part of a lesson, Back → home, no stored lesson;
      entering again starts fresh
- [x] 2.2 Browser: the ✕ and the finish button still leave no stored lesson
- [x] 2.3 Node suite, zprint, release compile with 0 warnings, desktop and
      mobile browser projects (the mobile INP budget in
      `add-form-stability.mobile.spec.js` failed under machine load and
      passed alone; the add form is untouched)
