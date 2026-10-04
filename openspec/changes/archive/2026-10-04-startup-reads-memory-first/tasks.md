# Tasks

## 1. Indexes

- [x] 1.1 `db.pouch` adds no `type` index of its own, and `init!` opens the databases without building any index
- [x] 1.2 The task queue owns its index and builds it when it starts; a failed build is logged and keeps the queue stopped

## 2. Start order

- [x] 2.1 The loader reads memory as soon as the databases are open (`:learner/read`), retries the whole read on any failure, and hands memory over once render exists (`:learner/memory`)
- [x] 2.2 The task queue starts once memory is loaded (`:learner/loaded`); a stop before then keeps it stopped

## 3. Verify

- [x] 3.1 Node tests, on the databases as `init!` opens them
- [x] 3.2 Browser specs for startup, memory and suggestions
- [x] 3.3 Release build, 1503 words / 7140 reviews: time to home with data, before and after
