## 1. Behaviour first

- [x] 1.1 Browser test: a lesson over new and known words serves one new word (`test/browser/lesson-word-choice.spec.js`); `npm run test:catalog`
- [x] 1.2 Browser tests that counted the review written on add follow the new rule (`lesson-submit.spec.js`)

## 2. Kernel

- [x] 2.1 FSRS-34 moves into `domain.retention`; the rate model it replaced goes to `dev.kernel-comparison`, its one reader
- [x] 2.2 `retention/pace` takes the cards of every word and collects the timed right answers itself
- [x] 2.3 `retention/memory` gives a word's FSRS-34 state; `retention/stability-gain` what a review adds to it
- [x] 2.4 `domain.lesson/lesson-words` takes gains and `random`: one new word, a 0.1 random share of known words, the rest by gain
- [x] 2.5 `use-cases.lesson/start` ranks by `stability-gain`; `use-cases.vocabulary/rows` reads recall from FSRS-34

## 3. No review on add

- [x] 3.1 `add!` and `rename!` write no review
- [x] 3.2 `add-review` goes into `use-cases.lesson/record-review!`; `find-duplicate` is inlined; `domain.vocabulary` is required as `vocabulary` there
- [x] 3.3 clj-kondo refuses a use case requiring another use case

## 4. Verify

- [x] 4.1 Unit tests, clj-kondo
- [x] 4.2 The desktop browser suite
- [x] 4.3 `smell-reviewer` on the branch diff
