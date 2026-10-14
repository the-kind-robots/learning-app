# Change: Lock example trials until successful word answers

## Why
Lesson examples can currently appear before the learner has correctly recalled the underlying word. That makes the lesson flow leak the target word too early and weakens recall practice.

## What Changes
- Store lesson trials with an explicit `locked?` flag.
- Start lessons with word trials unlocked and example trials locked.
- Unlock example trials only after a correct answer on the matching word trial.
- Keep progress based on all generated trials, including locked examples that already exist in the lesson state.
- Preserve random trial selection across the currently unlocked pool instead of forcing an example immediately after its word.

## Impact
- Affected specs: `specs/lesson/spec.md`, `specs/data-model/spec.md`
- Affected code: `src/client/domain/lesson.cljs`, `src/client/lesson.cljs`, client lesson tests
