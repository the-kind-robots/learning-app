# Tasks

## 1. Answer width (#409)

- [x] 1.1 `lesson.css`: `.lesson__answer-token` drops its horizontal padding; the hover, focus and open highlight grows outward with `box-shadow` instead.

## 2. Single advance (#277)

- [x] 2.1 Lesson view: continue buttons lose the `keydown` → `:action/click-if-enter` handler; the native button already clicks on Enter.
- [x] 2.2 `use-cases.lesson/advance!`: a call made while an advance is in flight joins it (a double click is two real clicks).

## 3. Verification

- [x] 3.1 Browser spec: answer with hints is as wide as the plain text, ±0.5 px.
- [x] 3.2 Browser spec: one Enter on ДАЛЕЕ is one click; Enter and double click log no save failure. Node test: two concurrent `advance!` calls, no error.
- [x] 3.3 Node tests, zprint, release compile with 0 warnings.
