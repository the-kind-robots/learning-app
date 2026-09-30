## Context

Colours live as RGB triplets in `resources/public/css/base/colors.css` and are used as
`rgb(var(--color-x))` or `rgba(var(--color-x), a)`. The token names are inherited from a
Duolingo-like kit; the owner has said they are not load-bearing. What is load-bearing is the leaf
palette: white, the green→yellow gradient (hsl 100→55) and purple. Behaviour is stated in
`specs/color-scheme/spec.md`; this file records how it is met.

A handful of blocks hard-code colours (`white`, `rgb(0 0 0 / a)` hairlines and hover tints,
`rgb(255 255 255 / a)` glass, two hex values in the install guide, `#000` in a `color-mix`), and the
retention mark on the words list is computed in `src/shared/utils.cljc` from literal owl and cardinal
values.

The splash is inline hiccup and CSS in `src/backend/core.clj`. It paints before any script runs, so
its scheme switch has to be CSS only.

## Goals / Non-Goals

**Goals:** a dark scheme by token override only; every component on tokens; a splash per scheme with
the same markup; still splash under reduced motion.

**Non-Goals:** a manual theme switch; renaming the inherited tokens; restyling the light scheme; a
per-scheme manifest `background_color`.

## Decisions

### One override block, the same token names

`colors.css` gets `@media (prefers-color-scheme: dark) { :root { … } }` redefining only the tokens the
app uses. Components keep reading the same names, so no selector changes per scheme. Renaming the
kit's tokens to semantic names (ground, surface, ink) was considered and left out: it touches every
block for no behaviour, and can be done later on its own.

One token is added, `--color-ink`: the pole opposite the ground (black in light, the violet-white text
in dark). It carries hairlines, hover tints and the darkening `color-mix` that used to hard-code black,
so those invert with the scheme. Shadows stay black: a shadow darkens in both schemes.

### Night-violet mapping

| Token | Role | Dark value | Contrast on ground |
|---|---|---|---|
| snow | ground, surfaces, label on filled buttons | `#150f1c` | — |
| polar | quiet surface (inputs, chips, hover) | `#211a2b` | 1.12 |
| swan | borders, disabled fill, track | `#3a3047` | 1.52 |
| hare | placeholder, disabled label, unknown retention | `#7d7189` | 4.11 |
| wolf | secondary text | `#ab9eb8` | 7.44 |
| gray-text | tertiary text | `#9a8da7` | 6.04 |
| eel | input and card text | `#e4dbeb` | 13.99 |
| black-text | body text | `#f3ecf8` | 16.26 |
| owl | primary green: fill, text, progress bar | `hsl(100 45% 60%)` | 9.37 (label on it 9.37; bar on swan 6.18) |
| tree-frog | button lip, correct text | `hsl(100 40% 50%)` | 7.29; 5.03 on sea-sponge |
| sea-sponge | correct background | `hsl(100 32% 16%)` | — |
| walking-fish | wrong background | `hsl(355 35% 18%)` | — |
| fire-ant | wrong text, error border | `hsl(0 85% 70%)` | 5.27 on walking-fish |
| cardinal | error text, danger | `hsl(0 90% 72%)` | 7.07 |
| macaw | accent blue: focus, caret, secondary buttons | `hsl(200 52% 62%)` | 7.77 (label on it 7.77) |
| whale | blue button lip | `hsl(200 45% 51%)` | 5.64 |
| iguana | badge, arrow-selected suggestion | `hsl(200 32% 22%)` | 1.67; eel on it 8.36 |
| humpback | install-guide accent text | `hsl(200 55% 78%)` | 11.57; 6.91 on iguana |
| beetle | purple tile accent | `hsl(285 80% 76%)` | 8.35 |
| ink | hairlines, tints | `#f3ecf8` | — |

Peacock, fox, starfish and bee are bright enough on the dark ground (8.6–12.1) and keep their values.
Blue keeps its meaning in dark rather than turning purple: the same control reads the same in both
schemes.

The green and the blue are calm light fills, not the firefly neon of the splash: the owner found
`hsl(100 88% 72%)` and `hsl(199 85% 65%)` too bright, and dark muted fills read as disabled. A light
fill with the ground as its label keeps both alive at about 8–9:1. Each lip is 10–11% darker in
lightness than its fill, so the 3D edge stays soft. The row picked with the arrow keys in the
suggestion list takes iguana in dark only: polar is 1.12:1 to the list there.

### Splash: one markup, two stylings

The ü's nine dots, their wobble wrappers and the sparkles are shared. Each dot's wrapper carries its
own custom properties — the light pulse delay along the stroke, the dark flicker duration and delay,
the wobble offsets, the two fills — and the stylesheet picks which ones run. The dark-only parts (the
halos, the stray fireflies, the foliage) are in the markup and hidden in light. Rendering two whole
splashes and hiding one was considered; it doubles the markup and leaves two title nodes in the DOM.

Firefly halos are radial-gradient fills animated by opacity and scale, not an animated `filter`, for
phone cost. The foliage is leaf paths filled with the ground colour and blurred 3 px, drawn above the
strays: a stray fades out behind a leaf that cannot be seen. The background layer uses
`preserveAspectRatio="xMidYMid slice"` so leaves stay cropped by the screen edge at any aspect.

`<meta name="color-scheme" content="light dark">` makes the browser paint a dark canvas before any
stylesheet, so a dark phone sees no white flash.

### Manifest background colour

A manifest holds one `background_color`. Chromium prototyped a `user_preferences.color_scheme_dark`
member (Intent to Prototype, TAG review #696), but no shipped browser reads it, so the manifest stays
white. The installed app's own splash is drawn by the OS from that value; the inline splash follows the
scheme once the page paints.

## Risks / Trade-offs

- [The foliage blur repaints the full-screen background each frame on a phone] → the blur is static on
  the leaf group and the leaves rotate by ±1.2° only; reduced motion stops it entirely. Measured cost on
  a real phone is not taken here.
- [`--color-ink` is a new name beside inherited ones] → it is the only token whose value is defined by
  the scheme's pole, and it is named for that role.

## Migration Plan

None: CSS and markup only. Rollback is reverting the commit.

## Open Questions

- Whether the client's own `.app-loading` placeholder («Загружаем...» after the script mounts) should
  carry the splash animation too. Left as it is.
