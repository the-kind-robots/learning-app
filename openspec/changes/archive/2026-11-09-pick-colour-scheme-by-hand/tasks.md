## 1. Tests first

- [x] 1.1 Browser tests: dark picked on a light phone at once and after a reopen (splash too), light on a dark phone, back to «Система», no scheme while the language is asked; regenerate `test/BEHAVIOR.md`

## 2. Stylesheets

- [x] 2.1 Move the night tokens from `colors.css` to `css/base/night.css`; `color-scheme` light and dark
- [x] 2.2 Settings menu 228 px wide with the row of three schemes

## 3. Server

- [x] 3.1 `colour-scheme` (shared): the pick from the cookie, the night media
- [x] 3.2 The head: the night stylesheet, `theme-color` and `color-scheme` from the pick
- [x] 3.3 The splash's night rules in a `<style>` under the night media

## 4. Client

- [x] 4.1 The page port reads and writes the pick and sets the night media; the pick re-applied at start
- [x] 4.2 The settings' row of schemes, its action and effect, the words in four languages

## 5. Installed app

- [x] 5.1 The manifests declare no `theme_color` or `background_color`

## 6. Verification

- [x] 6.1 Unit tests, lint, zprint; the touched browser specs pass
- [x] 6.2 Screenshots of the settings and a picked scheme, light and dark
- [x] 6.3 On the phone, from the dev stand: the pick, the splash after a reopen, the gesture strip at night
