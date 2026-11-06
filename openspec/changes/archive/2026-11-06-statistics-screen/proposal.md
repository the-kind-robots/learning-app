## Why

Today's stars say how much was done today; the owner wants a tap on them to show the rest: how the words hold and how the lessons went day by day, and a way to share it (#778).

## Changes

- A tap on today's stars opens a statistics screen, outside a lesson; it slides in like the word list.
- The words by memory: the number of words, a histogram of ten 10% bins at half opacity in the word list's red-to-green, the count in each, and under it one strip split into the four groups of the word list with their counts.
- The lessons by day: the number passed, then a column per day from the first lesson to today, half-opaque gold, purple from five lessons on, its date and weekday under it, today marked, the month's name sticking to the left edge; it opens on today and scrolls sideways.
- «ПОДЕЛИТЬСЯ» shares a picture of these numbers drawn on a canvas; without a share sheet the picture downloads.

## Capabilities

### New Capabilities
- `statistics`: the statistics screen — [delta](specs/statistics/spec.md).

### Modified Capabilities
- `screen-transitions`: opening the statistics slides in — [delta](specs/screen-transitions/spec.md).
- `app-navigation`: the statistics open over the screen they are opened from and close back to it — [delta](specs/app-navigation/spec.md), ADR-0026.

## Decisions

- Layout: the owner's pick on the mockup canvas (histogram from variant A3, lessons from A1, bars at 0.5).
- The stars open the screen only outside a lesson and outside the statistics itself: owner.
- Back, ✕ and `Escape` return to the screen the statistics were opened from, by a step back in the browser history: owner (ADR-0026).
- Two actions, as the learner has: `show-stats` on entry and on a change of memory alike (nothing to reset), `share-stats`; the stars button navigates itself.
- All words, not the active collection: owner.
- Every day is drawn at once: memory holds every lesson, and a year is 365 thin columns; nothing loads as the ribbon scrolls. Owner.
- The picture is drawn by hand on a 2D canvas, no new dependency: owner. Its layout is a pure function next to the screen's view (`pages.stats.picture`), shapes in the picture's pixels; the sharing port draws them and hands the PNG out: owner.
- Headings read «Слов: 258», «Уроков пройдено: 128», as «Попыток: 2» does: the interface's texts have no plural forms. Owner.
- The percent of a word is the word list's own (`use-cases.vocabulary/rows`), so a word reads alike in both places.
