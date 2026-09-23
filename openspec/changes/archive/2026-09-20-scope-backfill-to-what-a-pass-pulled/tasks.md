# Tasks

## 1. A pass reports what it brought

- [x] 1.1 `src/client/db/pouch.cljs` — collect the ids the pull wrote from the pass's `change`
      events and report them as `:pulled-ids`.
- [x] 1.2 `src/client/sync.cljs` — hand the pass result to the after-pass hook.
- [x] 1.3 Test: a real replication pass reports the ids it pulled and not the one it pushed.

## 2. Debt counted over what the pass brought

- [x] 2.1 `src/client/use_cases/examples.cljs` — `touched-word-ids`: the ids a pass wrote, plus every
      word named by a collection among them.
- [x] 2.2 `src/client/use_cases/examples.cljs` — `backfill!` takes `:all` or a pass's ids.
- [x] 2.3 `src/client/use_cases/examples.cljs` — `start-backfilling!`: the full pass at start, the
      hook afterwards, one at a time, and nothing at all for a pass that pulled nothing.
- [x] 2.4 `src/client/main.cljs` — the component starts it and hands back the hook.
- [x] 2.5 Tests: the start reads the whole vocabulary; a pass asks only about what it brought; a
      push-only pass reads nothing; an arrived collection owes for a word already here.

## 3. One statement of the rule

- [x] 3.1 `src/client/use_cases/examples.cljs` — `owed?` over a word's examples, used by
      `owed-requests` and by `owes-example?`.
- [x] 3.2 `src/client/use_cases/vocabulary.cljs` — the duplicate-word path calls `owes-example?`.
- [x] 3.3 `src/client/ports/examples.cljs`, `src/client/adapters/examples.cljs` — drop the
      single-pair `find` and its second copy of the rule.
- [x] 3.4 Test: adding a word to a theme queues what a backfill pass would queue, and nothing when
      the pair is answered.

## 4. Delivery

- [x] 4.1 The client node tests, the backend tests and the browser suite run.
- [x] 4.2 Sync the specs and archive the change on this branch.
