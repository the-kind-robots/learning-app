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
| snow | ground, surfaces | `#150f1c` | — |
| polar | quiet surface (inputs, chips, hover) | `#211a2b` | 1.12 |
| swan | borders, disabled fill, track | `#3a3047` | 1.52 |
| hare | placeholder, disabled label, unknown retention | `#7d7189` | 4.11 |
| wolf | secondary text | `#ab9eb8` | 7.44 |
| gray-text | tertiary text | `#9a8da7` | 6.04 |
| eel | input and card text | `#e4dbeb` | 13.99 |
| black-text | body text | `#f3ecf8` | 16.26 |
| raised | menus, the dictionary's list, dialogs | `#261b33` | — |
| snow-always-light | label on a coloured button, a flag's white | `#f3ecf8` | — |
| macaw | blue button fill; focus ring, caret, checkbox | `#2970a3` | 3.53; label on it 4.61 |
| whale | blue button lip | `#1f547a` | — |
| owl | green button fill, the lesson's progress bar | `#23792e` | label on it 4.72; bar on swan 2.27 |
| tree-frog | green button lip | `#18511f` | — |
| cardinal | red button fill | `#ba1c1e` | label on it 5.55 |
| fire-ant | red button lip | `#8e1517` | — |
| sea-sponge | right answer's panel | `#142416` | — |
| walking-fish | wrong answer's panel | `#290a0a` | — |
| thallium, thallium-dark | green on the ground: text, icons, memory scale, tile accent | `#6bc774` | 8.99; 7.77 on sea-sponge |
| neon, neon-dark | red on the ground: errors, delete, memory scale, tile accent | `#f76464` | 6.23; 6.10 on walking-fish |
| mercury | blue as a tile's or an example's accent | `#abd1ed` | — |
| iguana | badge, arrow-selected suggestion | `#27353f` | — |
| humpback | link and install-guide accent text | `#abd1ed` | — |
| beetle | purple tile accent | `hsl(285 80% 76%)` | 8.35 |
| mystic, mystic-text | a five-lesson day's bar, the count beside the stars | `#96f0d7`, `#a0f5d7` | — |
| star, star-*, star-glow | the star for a lesson: a sodium lamp | body `#ffa81e` | — |
| mystic-star, mystic-star-*, mystic-star-glow | the star for five lessons: a mercury lamp | body `#cdffee` | — |
| ink | hairlines, tints | `#f3ecf8` | — |

Peacock, fox, starfish and bee are bright enough on the dark ground (8.6–12.1) and keep their values.
Blue keeps its meaning in dark rather than turning purple: the same control reads the same in both
schemes.

The coloured buttons are deep fills under a light label, after the light of gas lamps: mercury blue
as it warms up (from the owner's `#3782b9`), thallium green, darkroom red. The owner set the depth by
the label: 4.6:1 on blue and green, 5.5:1 on red. A first take with light fills under a dark label
(about 8–9:1) was dropped: next to the stars the light green outshone them.

A deep fill is too dark to read as text on the ground (2.9–3.5:1), so a kit colour with two roles
splits. The animal token stays the fill and its lip; a token named after a lamp is the colour drawn
on the ground: thallium green, neon red, mercury blue, with `-dark` where the day needs the darker
shade. By day each is the value the place always read — owl or tree-frog, cardinal or fire-ant,
macaw — so the light scheme is unchanged; at night it is the light shade. The memory scale mixes
thallium and neon, so its bars keep their dark counts readable. The mix is `retention-colour/at` in the
client; it used to be `prozent->color` in the shared `utils`, which the server compiles too and has no
use for.

The stars are tokens too, five colours and a glow per star. The glow is a `filter` list: one shadow by
day, three at night. The light-up's flare is one shadow, and settles into a glow of three by
padding — the shorter list grows by transparent shadows — so the end of the light-up is the glow at
rest without a jump. The statistics' day bars take the stars' colours. The row picked with the arrow
keys in the suggestion list takes iguana in dark only: polar is 1.12:1 to the list there.

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
