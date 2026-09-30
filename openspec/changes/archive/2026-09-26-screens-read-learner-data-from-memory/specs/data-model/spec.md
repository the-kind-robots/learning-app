## REMOVED Requirements

### Requirement: Lesson documents are stored
**Reason**: The lesson in progress is no longer stored; it is held by the open app only (see `lesson`).
**Migration**: None. A lesson document left in device-db from an earlier version is never read.

### Requirement: Lesson trials include prompt, answer, and lock state
**Reason**: Trials are part of the lesson state, which is no longer stored.
**Migration**: None.
