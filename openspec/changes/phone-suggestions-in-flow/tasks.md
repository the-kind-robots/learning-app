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

## 5. Verify

- [x] 5.1 Measured after, stable over four runs: `submitPush` 94 px,
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
      *with a 180 ms height transition*, which spread each move over a dozen
      frames. Master has no such transition. The budget is left at 0.05 and the
      spec is red on it: whether flow is worth 0.0566, or worth reintroducing
      the animation for, is the owner's decision and it needs the real number.
- [x] 5.2 Rest of the browser suite green: 17 passed, the one failure being the
      budget assertion above (moved last in the spec so everything else is still
      evaluated and reported)
- [x] 5.3 Screenshots at 390x844 on the fixture dictionary: the device's fixed
      height (two rows in a 176 px box), master's overlay, and the list in flow
