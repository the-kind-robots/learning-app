## 1. Shell bar

- [x] 1.1 Solid white bar: full width, takes taps on its empty part (`app-shell.css`)
- [x] 1.2 `--app-bar-height` (68px); each screen starts its content below it (`foundation.css`, screen CSS)
- [x] 1.3 Info bar slot in the middle of the bar, holding the build mark (`application/view.cljs`, `app-shell.css`)

## 2. Verification

- [x] 2.1 Browser test: scrolling a long themes list leaves the bar's band unchanged (`collections-tiles.mobile.spec.js`)
- [x] 2.2 Screenshots of home and themes at 384×800 after the change
- [x] 2.3 Full browser suite, desktop and mobile
