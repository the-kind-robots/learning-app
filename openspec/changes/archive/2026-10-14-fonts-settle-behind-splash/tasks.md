## 1. Fonts

- [x] 1.1 Replace the 16 static Nunito files and `@font-face` blocks with two variable woff2 files (Cyrillic, Latin)
- [x] 1.2 Preload both files in the page head, before the stylesheet
- [x] 1.3 Install the render watch once the fonts are loaded or after 1 s (`main.cljs`)

## 2. Verification

- [x] 2.1 Browser spec: the regular and bold faces are loaded when the splash goes, and no font is requested after
- [x] 2.2 `npm run test:catalog`
