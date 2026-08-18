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
both directions, whatever they say.

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
- **The shift budget.** `shift.score < 0.05`, unfiltered, kept as it was.

## Risks / Trade-offs

- **The form moves while typing.** By design now. Bounded at 180 px, which is
  what the phone already does, and it buys back the translation field the
  overlay covered.
- **The shift score rises from zero, and past the budget.** Measured: 0.0566
  over three entries, `[0.0416, 0.0008, 0.0142]`, stable across runs. The first
  is the list opening to its ceiling and is the same shift the device already
  produces (0.0416 in one entry); the other two are the list narrowing from six
  matches to two, which is the shrinking the issue asks for. So the total is
  above the 0.05 budget while every individual move is no worse than today's.
  The budget is left where #289 set it and the spec is red on it — that is the
  decision, not a flake.
- **`svh` still ignores the keyboard.** Unchanged from before, and unchanged by
  this: `svh` is the small viewport height, deliberately the conservative one.

## Migration Plan

None. One media query in one stylesheet; no data, no storage, no service
worker involvement.

## Open Questions

- Whether 0.0566 justifies flow over the overlay, given that the displacement
  fell from 180 px to 94 px and that no single shift is worse than the device's
  present one. Owner's decision.
- If the budget is to hold, the lever is the height transition #289 measured:
  the same in-flow layout scored 0.028 with 180 ms of animation spreading each
  move over a dozen frames. Not reintroduced here — it was not asked for, and
  an animation makes a shift cheaper without making it smaller.
