## Why

At start the text is drawn in a fallback font for about half a second and then swaps to Nunito (#561): the fonts are 16 static files found only after `styles.css` is parsed, and nothing waits for them before the splash is replaced.

## Changes

- Sixteen static Nunito files give way to two variable woff2 files (Cyrillic, Latin; weights 200-1000). No italic file: italic is used nowhere.
- The page head preloads both files, before the stylesheet.
- The first screen replaces the splash only once the regular and bold faces are loaded, or after one second.

## Capabilities

### New Capabilities

### Modified Capabilities
- `learner-data-memory`: the splash is replaced only once the fonts are loaded or a second has passed (new requirement).

## Decisions

- Two files with `unicode-range`, not one merged file — merging variable fonts needs tooling the repo does not carry; both are preloaded, so the cost is one more parallel request.
- `font-display: swap` kept — the splash wait covers the first screen, and a face arriving after the cap still shows instead of staying invisible; `block` would hide text.
- The wait sits where the render watch is installed (`main.cljs`), not in the loader — one consumer, no new channel or flag.
