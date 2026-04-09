## 1. Lesson Entry Reset

- [x] 1.1 Change lesson entry so `GET /lesson` starts a fresh lesson session instead of reusing a persisted local lesson document.
- [x] 1.2 Add regression coverage for repeated lesson entry with an existing unfinished lesson state.

## 2. Review Progress Integrity

- [x] 2.1 Verify and, if needed, fix word-trial answer handling so it persists review data for retention/progress updates.
- [x] 2.2 Add regression coverage that word-trial answers create review documents while example-trial answers do not.

## 3. Verification

- [x] 3.1 Run lesson-related client tests and fix regressions.
- [x] 3.2 Manually confirm the production-like lesson flow no longer reopens the same stale session.
