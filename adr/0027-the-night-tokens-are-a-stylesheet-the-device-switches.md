# 0027. The night tokens are a stylesheet the device's pick switches

- Status: accepted, supersedes ADR-0017
- Date: 2026-11-09
- Supersedes: ADR-0017

## Context

ADR-0017 put the dark scheme in a redefinition of the colour tokens under a `prefers-color-scheme` media
query. The learner can now pick light or dark on the device, over the system's scheme (#805); what that
looks like is stated in `openspec/specs/color-scheme/spec.md`. A media query cannot say "the system's,
unless the device picked": the night needs a switch the page can set, without a second copy of the
tokens.

## Decision

Colour comes from the tokens in `resources/public/css/base/colors.css` and nowhere else: no block,
view or presenter names a literal colour. A scheme is a redefinition of those same tokens; components
carry no per-scheme selectors. A colour that must invert with the scheme — a hairline, a hover tint, a
darkening mix — reads `--color-ink`, the pole opposite the ground. Shadows are the one exception: they
stay black in every scheme.

The night's redefinition is its own stylesheet, `resources/public/css/base/night.css`, in the `base`
layer. The page links it with a `media` that says when the night applies: the system's dark preference,
`all` when the device picked dark, `not all` when it picked light. That media string is the one switch
of the scheme; the server renders it from the device's cookie, and a pick in the open app sets it again.

A colour that is both a fill under a label and a mark on the ground splits when a scheme needs two
values for it: the token keeps the fill, and a new token, named after a lamp (thallium, neon,
mercury), is the colour drawn on the ground — text, icons, scales, accents. By day the two are equal.
A new token is named for its role or its night light, never for a colour a scheme redefines.

The server-rendered splash is outside the token rule. It paints before the stylesheet can be relied on,
so it carries its own colours for both schemes; its night rules sit in a `<style>` with the same media
string.

## Consequences

- A new component is dark-ready if it uses tokens; a literal colour in review is a defect.
- The night tokens exist once; a third scheme would be another stylesheet with its own media.
- Whatever else must change with the scheme — the browser's `theme-color`, the splash — carries the same
  media string, or it shows the system's scheme against the pick.
- The inherited token names stay: renaming them to roles is separate work with no behaviour.
