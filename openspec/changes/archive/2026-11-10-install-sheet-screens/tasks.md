## 1. Tests

- [x] 1.1 Browser spec: both manifests give the shortcuts «Урок» and «Слова» that open the lesson and the word list, the identity `/home`, phone screenshots only, and every image at the size it declares; `npm run test:catalog`

## 2. Images

- [x] 2.1 Maskable icons, production and development (red D inside the safe zone), 192 and 512
- [x] 2.2 Shortcut icons for «Урок» and «Слова»
- [x] 2.3 Five phone screenshots from seeded test data, the dev D hidden, in `resources/public/screenshots/`

## 3. Manifests

- [x] 3.1 `manifest.json` and `dev-manifest.json`: `id`, Russian `name`/`short_name`/`description`, `lang`, maskable icons, screenshots, shortcuts

## 4. Verify

- [x] 4.1 The new spec passes; the service worker precache list leaves the screenshots out
- [x] 4.2 Chrome parses both manifests with no errors or warnings
- [ ] 4.3 The phone: the install offer shows the screenshots, the installed icon and the two shortcuts — not checked before the merge, which the owner called first; checked on production after the deploy
