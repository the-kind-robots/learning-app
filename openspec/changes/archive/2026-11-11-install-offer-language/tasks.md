## 1. Tests

- [x] 1.1 `install-manifest.spec.js`: a browser on Turkish gets the Turkish manifest; every image of every language's manifest loads at its declared size; offline after a first visit the page has its manifest; a Turkish read online is what the browser gets offline, for both manifests; `npm run test:catalog`

## 2. Manifest

- [x] 2.1 `web-manifest/manifest` builds it from the dictionaries; `/manifest.json` and `/dev-manifest.json` answer it in `page-language`, with `Vary: Accept-Language`; the static files removed
- [x] 2.2 `:install/*` in the four dictionaries
- [x] 2.3 Screenshots in `resources/public/screenshots/<language>/`, the Russian ones moved there; a language without its own gets the English ones (`web_manifest_test.clj`)
- [x] 2.4 The worker precaches the manifests, reads them from the network first and answers offline with the last copy

## 3. Verify

- [x] 3.1 The spec passes; each language's manifest read with its `Accept-Language`
- [ ] 3.2 The phone: the install offer in the phone's language — not checked before the merge; the owner chose to archive first and check on the phone after the deploy
