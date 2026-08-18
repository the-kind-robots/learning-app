# Tasks

## 1. Baseline

- [x] 1.1 Record `submitPush` and `shiftScore` on master's overlay, and on the
      device's fixed-height layout, from `npx playwright test --project=mobile
      --no-deps`

      | layout | submitPush | shiftScore | entries |
      |---|---:|---:|---:|
      | master, overlay | 0 px | 0 | 0 |
      | device, `height: min(176px, 34svh)` in flow | 180 px | 0.0416 | 1 |

## 2. Stylesheet

- [x] 2.1 Phone media query: `position: static`, `left/right: auto`,
      `max-height: min(176px, 34svh)` — a ceiling, not a height
- [x] 2.2 Replace the comment that argued for the overlay with the ceiling's
      own reasoning: in flow the ceiling is the maximum displacement

## 3. Mobile spec

- [x] 3.1 Assert no emptiness: at two rows `clientHeight === scrollHeight`, and
      under the ceiling. (`boundingBox().height` is the border box and runs 2 px
      larger on the list's 1 px border, so the comparison is padding-box to
      padding-box.)
- [x] 3.2 Assert bounded displacement: `submitPush === listHeight + 4` and
      `submitPush <= 180`
- [x] 3.3 Keep the value field and panel heading assertions, and the
      `shift.score < 0.05` budget
- [x] 3.4 Update the spec's header comment: it describes the overlay it was
      written for

## 4. Supersede the overlay record

- [x] 4.1 Note in `openspec/changes/mobile-add-form-stability/` — design,
      proposal, tasks — that the overlay is superseded by #373, leaving its
      reasoning intact

## 5. Height animation

- [x] 5.1 Bring the transition back against the content height, not a fixed
      one: `interpolate-size: allow-keywords` with `height: auto` open and
      `height: 0` closed, inside `@supports` so unsupported browsers keep the
      flow, the ceiling and the content height with no animation
- [x] 5.2 Keep `:empty` displayed with zeroed border and padding on the phone —
      a `display: none` box cannot transition
- [x] 5.3 Measure what it reaches: 0.0502 / 0.0502 / 0.0544 over three runs
      against 0.0566 without. The opening spreads into nine entries totalling
      0.036; the list *resizing* does not animate at all (`auto` -> `auto` is
      no change of computed value) and keeps its 0.0142 entry unchanged.
      300 ms was worse than 180 ms (0.0533), so duration is not a lever.

## 6. Verify

- [x] 6.1 Measured after, stable over four runs: `submitPush` 94 px,
      `listHeight` 90 px (88 px of rows, 1 px border each side), `renders` 14,
      `inp` 48-56 ms — and `shiftScore` **0.0566**, three entries
      `[0.0416, 0.0008, 0.0142]`.

      The displacement fell as expected: 180 px -> 94 px. **The shift score did
      not.** The first entry, 0.0416, is the list opening to its ceiling and is
      exactly the shift the device already produces today; the other two are the
      list narrowing from six matches to two while the word is typed — the very
      shrinking this change exists to deliver. A box that shrinks moves the page
      each time it shrinks.

      The 0.05 budget was set in #289 against an in-flow list that scored 0.028
      *with a 180 ms height transition*. The transition is now back (task 5),
      run against the content height rather than a fixed one, and it brings the
      score to **0.0502** — still over the budget. #289's 0.028 does not
      reproduce because that box's open height was fixed and never resized
      again; a content-sized box resizes on every change of match count, and a
      CSS transition cannot see a resize driven by content.

      The budget is left at 0.05 and the spec is red on it. That is reported,
      not adjusted: the owner chose the animation on the strength of 0.028, and
      0.028 is not what this layout produces.
- [x] 6.2 Rest of the browser suite green: 17 passed, the one failure being the
      budget assertion above (moved last in the spec so everything else is still
      evaluated and reported)
- [x] 6.3 Screenshots at 390x844 on the fixture dictionary: the device's fixed
      height (two rows in a 176 px box), master's overlay, and the list in flow.
      Re-shot with the animation: the resting picture is pixel-identical, since
      the animation changes how the box gets to its height, not the height
