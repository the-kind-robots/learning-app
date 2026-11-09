# 0017. Colour schemes are overrides of one token set

- Status: accepted
- Date: 2026-09-30

## Context

The app gains a dark scheme that follows the system preference (#413). What the schemes must look like
and how readable they must be is stated in `openspec/specs/color-scheme/spec.md`, not here. This
records where scheme colours live, so the next component and the next scheme land in the same place.

Before this, most blocks already read RGB-triplet tokens from `base/colors.css`, but some hard-coded
white, black and hex values, and one presenter computed a colour from literal RGB values.

## Decision

Colour comes from the tokens in `resources/public/css/base/colors.css` and nowhere else: no block,
view or presenter names a literal colour. A scheme is a redefinition of those same tokens under a
`prefers-color-scheme` media query; components carry no per-scheme selectors. A colour that must invert
with the scheme — a hairline, a hover tint, a darkening mix — reads `--color-ink`, the pole opposite the
ground. Shadows are the one exception: they stay black in every scheme.

A colour that is both a fill under a label and a mark on the ground splits when a scheme needs two
values for it: the token keeps the fill, and a new token, named after a lamp (thallium, neon,
mercury), is the colour drawn on the ground — text, icons, scales, accents. By day the two are equal.
A new token is named for its role or its night light, never for a colour a scheme redefines.

The server-rendered splash is outside this rule. It paints before the stylesheet can be relied on, so
it carries its own inline colours for both schemes.

## Consequences

- A new component is dark-ready if it uses tokens; a literal colour in review is a defect.
- A future manual switch can set the same overrides on an attribute selector without touching blocks.
- The inherited token names stay: renaming them to roles is separate work with no behaviour.
