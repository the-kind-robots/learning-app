## 1. Home UI Stability

- [ ] 1.1 Fix footer animation so it does not replay on every return to the home screen.
- [ ] 1.2 Fix mobile add-word help/overlay behavior so it does not cover the input field.
- [ ] 1.3 Fix word-list edit UI jitter and visual instability.
- [ ] 1.4 Fix the transition when deleting the last word so the empty-state screen appears without a visible jerk.
- [ ] 1.5 Fix the first-word add flow so the add-word panel does not jump vertically when the lesson footer appears.

## 2. Lesson UI Stability

- [ ] 2.1 Fix prompt/task-card jumping during answer checking on the lesson screen.
- [ ] 2.2 Fix closely related lesson-screen layout shifts discovered during verification.
- [ ] 2.3 Fix lesson entry CTA so the initial `ПРОВЕРИТЬ` footer matches the `НАЧАТЬ УРОК` footer position and button size without a visible jump.
- [ ] 2.4 Fix post-check lesson keyboard flow so `Space`/`Enter` can activate `ДАЛЕЕ` or finish without reaching for the mouse.

## 3. Nearby Glitches

- [ ] 3.1 Capture and fix other small home/lesson UI glitches found while verifying the same interaction flows.

## 4. Verify

- [ ] 4.1 Verify desktop home flow after the fixes.
- [ ] 4.2 Verify mobile home flow after the fixes.
- [ ] 4.3 Verify lesson answer-check flow after the fixes.
- [ ] 4.4 Verify the home-to-lesson footer transition keeps CTA position and size visually stable.
- [ ] 4.5 Verify the first-word add flow keeps the add panel visually stable when the lesson footer appears.
- [ ] 4.6 Verify keyboard-only lesson progression after answer checking on desktop.
