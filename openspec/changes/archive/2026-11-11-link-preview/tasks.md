## 1. Tests

- [x] 1.1 `link-preview.spec.js` covers each scenario of the delta, every language's picture at its size; `npm run test:catalog`

## 2. Preview

- [x] 2.1 `scripts/link-preview.mjs` and its header screenshots; the four pictures it writes
- [x] 2.2 The tags in the page head; the pictures' addresses, by language, computed once at start

## 3. Verify

- [x] 3.1 The spec passes
- [ ] 3.2 A real messenger, on production after the deploy: Telegram and WhatsApp, from a phone in Russian and one in another language. Before it, any backend without `LEARNING_APP__PUBLIC_URL` names the pictures at `https://sprecha.de`, which serves none until this ships.
