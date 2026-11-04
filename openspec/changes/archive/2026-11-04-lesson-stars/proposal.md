## Why

A lesson is three trials, and its end offered only «ЗАКОНЧИТЬ»: one more lesson meant going home and starting again. Nothing showed how much was done today (#715).

## What Changes

- The end of a lesson offers «ЕЩЁ УРОК» first, which starts a new lesson at once, and a quiet «ЗАКОНЧИТЬ» below it, which goes home.
- A lesson passed is written to the learner's data as a record of its own, beside the reviews: added only, never changed, replicated with the account.
- The middle of the shell bar shows today's lessons as stars, on every screen: gold stars, five of them become one purple star, five purple and more read as a number and one purple star («5★»). A new star lights up quietly.
- Design: the canvas linked from #715.

## Capabilities

### Modified Capabilities
- `lesson`: the end of a lesson, and the record of a passed lesson.
- `app-navigation`: the shell bar's middle slot shows today's stars.

## Impact

- `adapters.learner` (a new `lesson` document type in user-db, memory takes it; snapshot format version 7), `ports.learner`, `use-cases.lesson`.
- Lesson footer view, shell view and presenter, `app-shell.css`, `lesson.css`, i18n strings.
