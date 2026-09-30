## 1. Task queue

- [x] 1.1 A retry-after result pauses the runner: workers start no new task, the cycle ends, and a flush during the pause schedules itself for the end of the pause
- [x] 1.2 Unit test in `test/client/tasks_test.cljs`: ten due tasks, the first throttled; three run, a flush and a new task during the pause start nothing, and after the pause all run
- [x] 1.3 Node test suite green
