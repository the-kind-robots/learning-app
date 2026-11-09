## Decisions

### The pick is a cookie the server reads

The device's pick is the cookie `scheme`: `light` or `dark`, absent for the system's (`shared/colour_scheme.cljc`
reads it on both sides). The client writes it for 400 days, Chrome's cap, and writes it again at every
start: the clock starts over, and a page the service worker cached under an older pick gets the current
one. The server renders the head and the splash from it, so the first paint is already in the picked
scheme. localStorage was the other home; the server cannot read it, and the splash would show the
system's scheme until the script runs.

### One media string switches every night rule

`colour-scheme/night-media` turns the pick into the media under which the night applies:
`(prefers-color-scheme: dark)` with no pick, `all` for dark, `not all` for light. Three elements carry it:
the `<link>` of `css/base/night.css`, the dark `theme-color` meta, and the splash's night `<style>`. The
dark `theme-color` comes before the white one: the browser takes the first that matches. A pick in the
open app sets the same string on the same elements, so nothing reloads.

`color-scheme` is `light` in `colors.css` and `dark` in `night.css`, so controls and scrollbars follow the
pick, not the phone; the `color-scheme` meta (`light dark`, `light` or `dark`) covers the canvas before the
stylesheet.

### The pick reaches the screen through the page port

`ports.page` reads the cookie (`scheme`) and writes it (`choose-scheme!`); `application/state` adds
`:device/scheme` on every render, as it adds `:system/language`. No key of the interface's state holds it:
the radio the learner pressed stays checked by the browser until the next render reads the cookie.

### Named `scheme`, not `theme`

«Тема» and "theme" already name the collections (the themes screen, «Без темы»). The code and the cookie
say `scheme`; the settings' group is named «Оформление» for screen readers.

## Risks

- [A dark pick on a light phone leaves the installed app's gesture strip light] → Chrome paints it from
  the manifest at install and reads no per-scheme colour there; with no manifest colour it follows the
  phone. Accepted.
- [An installed app keeps the white strip until Chrome refreshes its WebAPK] → it does so on its own
  within days; reinstalling applies it at once.
- [An offline start shows the splash of the pick the worker's copy was cached under] → the app re-applies
  the current pick as it starts.
