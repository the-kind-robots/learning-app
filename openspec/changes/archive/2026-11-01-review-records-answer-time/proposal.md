## Why

The FSRS-34 kernel of #692 rates a review Again / Hard / Good / Easy from how long the answer took, and a review records only whether the word was retained. Time has to be recorded first, so that answers start to accumulate before the kernel lands.

## Changes

- A review written in a lesson carries `answer-ms`, the time from the trial shown to the answer sent.
- No time is recorded when the page was hidden during the trial.
- Nothing on screen changes; retention still reads only `retained` and `created-at`.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `data-model`: the review document shape gains an optional `answer-ms`.
- `lesson`: a graded word or phrase trial records its time to answer.

## Decisions

- The interval is measured on a monotonic clock of the device, not as the difference of two wall-clock times — a clock change during a trial cannot spoil it.
- Hidden page voids the time rather than storing it with a ceiling — a background tab says nothing about memory (#692, decision 2).
- Raw milliseconds are stored, the rating is not — the rating depends on thresholds that move with the learner's answers (#692).
- Wrong and blank answers record their time too; the rating ignores it there, the replay of #513 may not.
- Old reviews are not migrated: no `answer-ms` means no time.
