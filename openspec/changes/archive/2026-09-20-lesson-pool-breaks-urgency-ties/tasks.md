## 1. Specs

- [x] 1.1 State what the pool does when more items tie for it than it holds.

## 2. Implementation

- [x] 2.1 Return the sort key on each vocabulary row; leave the list's order alone.
- [x] 2.2 Break ties in `domain.lesson/pick-vocab` before the cut.
- [x] 2.3 Hand `pick-vocab` every row instead of a pre-trimmed head.

## 3. Verify

- [x] 3.1 Confirm `cljs.core/sort` is stable before relying on shuffle-then-sort.
- [x] 3.2 Cover a tied pool being cut differently across draws, at both layers.
- [x] 3.3 Cover strict urgency still winning every time.
- [x] 3.4 Cover the second-granularity tie in `domain.retention`.
- [x] 3.5 `npx shadow-cljs compile node-test`.
