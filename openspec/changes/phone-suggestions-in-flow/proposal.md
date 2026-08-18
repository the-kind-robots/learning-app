# Proposal: phone-suggestions-in-flow

## Why

On a phone the suggestion list does not shrink to one or two rows. Measured on
the device build, the phone branch of `resources/public/css/components/autocomplete.css`
sets a *fixed* height — `position: static` plus `height: min(176px, 34svh)` —
so the box stays 176 px at one row, at two rows and at ten. The emptiness under
the last suggestion is that fixed height, not flex stretching, not extra nodes,
and not the software keyboard: all three were excluded by measurement. GitHub
issue: #373.

Master no longer has the fixed height — it has `max-height: min(240px, 34svh)`
with the list lifted out of the flow as an overlay (#289), which gives 89 px on
two rows but takes the list out of the page and lays it over the translation
field (#349). The owner wants the list back in the flow *and* sized by its
content.

## What Changes

- The phone suggestion list returns to the flow (`position: static`) and is
  bounded by `max-height: min(176px, 34svh)` instead of a fixed height, so one
  row is one row high and ten rows scroll inside the ceiling.
- The ceiling is **176 px, not the desktop 240 px**, and that is the whole
  reason a number is being picked rather than inherited. In flow, the list's
  ceiling *is* the maximum displacement of everything below it. At 240 px a
  long list would push the translation field and the submit button down 248 px
  — worse than the 180 px the phone already lives with. At 176 px the worst
  case equals what the device already does today, while one and two rows give
  49 px and 89 px instead of an unchanging 176 px. Strictly no worse than the
  present behaviour in any case, and better in the cases the issue is about.
- `test/browser/add-form-stability.mobile.spec.js` stops asserting that the
  translation field and the submit button never move — that assertion is the
  overlay written down, and with the list in flow it fails by construction. It
  asserts instead that the box has no emptiness in it, that the displacement is
  bounded, that the value field and the panel title still hold still, and that
  the unfiltered layout-shift score stays inside the 0.05 budget.
- The overlay decision recorded in `openspec/changes/mobile-add-form-stability/`
  is marked as superseded by #373 with a pointer here. Its reasoning is left
  intact: it was correct for the measurements it had.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `home-add-form-focus`: the suggestion list's height on a phone is a stated
  requirement — content-sized under a ceiling, and the ceiling bounds how far
  the form below it can be pushed.

## Impact

- `resources/public/css/components/autocomplete.css` — the phone media query:
  back into the flow, fixed height replaced by a ceiling.
- `test/browser/add-form-stability.mobile.spec.js` — the overlay assertions
  replaced by flow assertions; the same numbers still logged on every run.
- `openspec/changes/mobile-add-form-stability/` — superseded notes only, no
  reasoning rewritten.
- Behaviour: the translation field and the submit button move again while the
  user types, by at most the list's height plus its 4 px margin. That is the
  trade the owner is buying flow with, and the PR carries the measured number
  rather than a promise about it.
