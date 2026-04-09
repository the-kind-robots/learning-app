## 1. Specify Desired Focus Behavior

- [x] 1.1 Capture the expected desktop and mobile post-submit focus behavior in proposal, design, and spec artifacts.

## 2. Implement Desktop-Only Focus Restoration

- [x] 2.1 Restore focus to `#new-word-value` from the successful OOB add-form replacement.
- [x] 2.2 Gate the behavior so mobile/touch-first devices are not forced to refocus the input.

## 3. Verify

- [x] 3.1 Run a real desktop browser add-word flow with repeated submits and confirm focus returns after each success.
- [x] 3.2 Run `npx shadow-cljs compile app` and address regressions.
