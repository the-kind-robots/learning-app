# Tasks

## 1. Scheduler

- [x] 1.1 `pouch/sync-once!` reports the revisions it pulled
- [x] 1.2 `start!` runs one pass loop; requests during a pass set one
      follow-up pass
- [x] 1.3 The change feed requests a pass only for revisions no pass pulled
- [x] 1.4 Remove the 30 s gate, `dirty`, `pull-due?`, `pass-interval-ms` and
      the `pull-skipped` trace

## 2. Verify

- [x] 2.1 Node: concurrent requests give one pass; a request during a pass
      gives exactly one more; a failed pass does not block the next; the
      pull's own writes request nothing
- [x] 2.2 Node on 20 and 24, zprint, release compile with 0 warnings, desktop
      dictionary and add-word browser specs
