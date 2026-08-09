## 1. Assets

- [x] 1.1 Generate `icons/ue-dev-192.png` and `icons/ue-dev-512.png` — current icons with a red D badge in the corner
- [x] 1.2 Add `dev-manifest.json` referencing the badged icons

## 2. Wiring

- [x] 2.1 Backend shell: manifest link picks dev manifest via `running-from-source?`
- [x] 2.2 Service worker: precache dev manifest and badged icons

## 3. Verify

- [x] 3.1 Browser check: dev stand serves dev manifest with badged icons; icons render with the badge
