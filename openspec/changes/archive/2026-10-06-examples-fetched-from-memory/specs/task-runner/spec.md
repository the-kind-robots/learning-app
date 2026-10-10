## REMOVED Requirements

### Requirement: Task documents are persisted

**Reason**: Its only task type was the example fetch, which now runs from memory with no stored
document (`specs/example-backfill/spec.md`).
**Migration**: Leftover task documents are deleted at start (`specs/data-model/spec.md`).

### Requirement: Task runner processes tasks in parallel

**Reason**: No task runner.
**Migration**: Requirement: Example requests are paced (`specs/example-backfill/spec.md`).

### Requirement: Offline pause

**Reason**: No task runner.
**Migration**: Requirement: Example requests are paced (`specs/example-backfill/spec.md`).

### Requirement: Retry with exponential backoff

**Reason**: No task runner.
**Migration**: Requirement: Each failure has one effect, and nothing is retried forever
(`specs/example-backfill/spec.md`).

### Requirement: Task cleanup is required

**Reason**: No task documents are written.
**Migration**: Leftover task documents are deleted at start (`specs/data-model/spec.md`).

### Requirement: Unknown tasks are dead-lettered

**Reason**: No task documents are written, so none can be of an unknown type.
**Migration**: None.

### Requirement: Task runner starts on main-thread app boot

**Reason**: No task runner.
**Migration**: Requirement: Nothing is asked for before the device knows what the account holds
(`specs/example-backfill/spec.md`); Requirement: App boots from main thread entry point
(`specs/main-thread-runtime/spec.md`).
