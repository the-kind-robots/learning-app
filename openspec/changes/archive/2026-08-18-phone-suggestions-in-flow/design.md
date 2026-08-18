# Design: phone-suggestions-in-flow

## Context

Three layouts for the phone suggestion list exist in this repository's history,
and #373 is about telling them apart.

| where | phone rule | 1 row | 2 rows | 10 rows | pushes the form |
|---|---|---:|---:|---:|---|
| device build | `position: static` + `height: min(176px, 34svh)` | 176 px | 176 px | 176 px | 180 px, always |
| master today | overlay + `max-height: min(240px, 34svh)` | 49 px | 89 px | 240 px | 0 px |
| this change | `position: static` + `max-height: min(176px, 34svh)` | 49 px | 89 px | 176 px | height + 4 px |

The defect the issue reports is the first row of that table: a *fixed* height,
which is why the box holds 176 px whether it has one suggestion in it or ten.
Flex stretching, extra nodes and the software keyboard were each excluded by
measurement before the cause was named.

Master already fixed the shrinking, by a route the owner does not want: #289
lifted the list out of the flow into an overlay, so it shrinks but covers the
translation field (#349). The owner wants both — the list in the page flow, and
sized by what is in it.

No in-force ADR constrains this; ADRs 0001-0011 are about the dictionary, the
runtime graph, identity, sync and phrases. The layout decision lives in the
change documents.

## Goals / Non-Goals

**Goals.** One or two rows occupy one or two rows' height on a phone with the
keyboard open (#373's acceptance). The list sits in the flow. The form below it
is never displaced further than it already is today on the device.

**Non-Goals.** The overlay's own defect (#349, the list covering the
translation field) is not fixed here — it is dissolved, because a list in the
flow covers nothing. Desktop is untouched: it keeps the overlay and the 240 px
ceiling, where there is room for both.

## Decisions

### The ceiling is 176 px, not the desktop's 240 px

This is the only number in the change that had to be chosen, and in flow it is
not a cosmetic bound: **the list's ceiling is the maximum displacement of the
form below it.** Whatever the list can grow to, the translation field and the
submit button drop by that much plus the list's 4 px margin.

- At 240 px the worst case is a 244 px push — worse than the 180 px the phone
  already lives with today, so keeping the desktop number would make the tall
  case worse while fixing the short one.
- At 176 px the worst case is 180 px: *exactly* what the device does today,
  except that today it does it at one row as well as at ten.

So the change is strictly no worse than the present behaviour in every case,
and better in the case the issue is about: 49 px at one row and 89 px at two,
against an unchanging 176 px.

`34svh` stays beside it for the same reason it was introduced: on a short
screen a third of the viewport is a better bound than any pixel count, and
`svh` is the *small* viewport height, so it already assumes the keyboard is up.

### Flow is bought with movement, and the price is measured, not argued

An overlay moves nothing; a list in the flow moves everything under it. That is
the trade, and it is the owner's to make, so this change reports the number
rather than reasoning about it.

One thing the #289 design says outright must be re-checked rather than carried
over: the low shift score it measured in flow (0.028) was held down by a 180 ms
height animation, which spread the movement over a dozen frames. That animation
does not exist in master — there is no `height: 0`, no `transition: height`
left to remove. So the score should not be expected to fall in proportion with
the displacement. Measurements are in `tasks.md` and in the pull request, in
both directions, whatever they say. It did not fall in proportion, and the
animation was then brought back to try — see the next section for what that
could and could not reach.

### The height animation returns, and only half the movement is animatable

The owner chose to bring back the height transition #289 removed, on the
strength of that change's 0.028 against this one's 0.0566. Returning it as it
was is not available: the old rule animated `height` to `min(176px, 34svh)`,
and a fixed open height is the defect this change exists to remove. An
animation must therefore run to the *content*.

`transition: height` interpolates between two values, and `auto` is not one
until `interpolate-size: allow-keywords` makes it one — shipped in Chrome 129,
and this is a Chromium phone PWA. So the open state is `height: auto` with the
transition on it, and the closed state stays what it was before #289: height 0,
opacity 0, no border, no padding, `:empty` displayed rather than hidden because
a `display: none` box cannot transition. The whole animation sits inside
`@supports (interpolate-size: allow-keywords)`; where that is missing the block
is skipped and the list keeps the flow, the ceiling and the content height, with
the sizing landing in one frame. Nothing about #373's acceptance depends on the
animation — no emptiness at rest or at the end of the transition either way.

What the measurement then showed, and it is the point: **the animation covers
the opening and cannot cover the resizing.** `0 -> auto` is a change of
computed value and animates. `auto -> auto` is not a change of style at all —
the used height moves because the content changed — so no transition fires and
the list narrowing from six matches to two lands in a single frame, exactly as
it did without the animation. Its 0.0142 entry is identical in both.

That is why #289's 0.028 does not reproduce here. That number was one animated
move of a box whose open height was fixed, so it never resized again; a
content-sized box resizes on every keystroke that changes the match count, and
those are the shifts CSS cannot reach. Only JavaScript could, by writing an
explicit height, and an explicit height is what this issue is about not having.

### The mobile spec is rewritten, not relaxed

`test/browser/add-form-stability.mobile.spec.js` asserts today that the
translation field and the submit button do not move at all. That is the overlay
written down as a test; with the list in flow it fails by construction, and
weakening it to a tolerance would leave a spec that no longer says anything.

It is replaced by assertions about the property that actually matters now:

- **No emptiness.** At two rows, `clientHeight === scrollHeight`, and the box
  well under the ceiling. Both are padding-box measurements, which is why
  `boundingBox().height` is not one of them — it is the border box and reads
  2 px larger on the list's 1 px border. This is #373's acceptance criterion
  stated as a number, and a fixed-height box fails it at two rows.
- **Bounded displacement.** `submitPush === listHeight + 4`, and
  `submitPush <= 180`. The equality says the push is the list and nothing else;
  the bound says it is no worse than the device's present behaviour.
- **The anchor holds.** The value field and the panel heading do not move.
  `justify-content: flex-start` on `.home__content` is what holds them (master
  changed it from `center` for exactly this reason), so these assertions guard
  a real regression: if that ever goes back to `center`, half the list's height
  travels upward and takes the field out from under the finger.
- **The shift budget.** `shift.score < SHIFT_BUDGET`, unfiltered. The budget
  moved with the layout — 0.05 to 0.06 — and the section below says why.

### The 0.05 budget belonged to the fixed height; 0.06 belongs to this one

Raising a threshold because the code no longer meets it is how thresholds stop
meaning anything, so the reason is recorded rather than the number alone.

0.05 came from #289, measured against a list whose open height was **fixed**:
it opened once and never resized, whatever the matches did afterwards. One
move, one budget. This change removed that fixed height precisely because a box
that cannot shrink is the defect — and a box sized by its content resizes on
every change of match count, moving the form under it each time. Those extra
shifts are the layout's own property, not a fault in its implementation.

They cannot be animated away either. The transition covers `0 -> auto`, a
change of computed value; a resize is `auto -> auto`, which changes no computed
value at all — only the used height moves, and transitions do not listen to
used values. Measured, the resize entry (0.0142) is identical with the
animation and without it, and the floor of the whole layout is around 0.049
even if every entry could be spread across frames.

So the choice was between a budget no correct implementation of this layout can
meet and a budget that matches the layout that was chosen. 0.06 is the second:
the measured 0.0502-0.0544 over five runs plus room for that spread — about
10 % over the worst observed — and not room for a regression. A regression here
moves the geometry, and the geometry assertions are exact equalities, not
budgets.

One thing this budget cannot borrow from is CLS. Every entry counted here
carries `hadRecentInput`, since typing is what causes them, so CLS discards all
of it and reads 0.000 no matter what this number does. The budget is internal
discipline; that is exactly why it has to describe the layout actually shipped.

## Risks / Trade-offs

- **The form moves while typing.** By design now. Bounded at 180 px, which is
  what the phone already does, and it buys back the translation field the
  overlay covered.
- **The shift score rises from zero, and the budget moves with the layout.**
  Without the animation: 0.0566 over three entries, `[0.0416, 0.0008, 0.0142]`.
  With it: 0.0502-0.0544 over five runs, the opening spread into nine entries
  totalling 0.036 and the resize keeping its 0.0142 untouched. Splitting a move
  across frames only helps through the impact fraction — a measured 13 % — so
  even a hypothetical animation covering every shift lands near 0.049: the
  floor of this layout, not a number better code reaches. The budget was raised
  to 0.06 with the layout it belongs to (see the decision above), and the spec
  is green on it.
- **A longer transition does not help.** 300 ms scored 0.0533 against 180 ms's
  0.0502 — more entries, no less total travel. Measured before settling on
  180 ms.
- **`svh` still ignores the keyboard.** Unchanged from before, and unchanged by
  this: `svh` is the small viewport height, deliberately the conservative one.

## Migration Plan

None. One media query in one stylesheet; no data, no storage, no service
worker involvement.

## Open Questions

- Settled: flow at 0.0502 was chosen over the overlay at 0 — the displacement
  fell from 180 px to 94 px and no single shift is worse than the device's
  present one — and the budget was raised to 0.06 with the layout. The
  animation lever is spent: it was worth 0.006, not the 0.028 the earlier
  change reported.
- Should the score ever need to come down, what remains is not a CSS lever:
  lower the ceiling (less travel, shorter list), or animate the resize from
  JavaScript by writing an explicit height per render — which reintroduces the
  explicit height this issue exists to remove, only computed. Neither is taken
  here.
