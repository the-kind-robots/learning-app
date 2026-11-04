## 1. Backend

- [x] 1.1 The mail: an HTML part beside the plain-text one, the buttons before the note
- [x] 1.2 The page: styles inlined from `resources/invite-decision.css`, no link to the app's stylesheet

## 2. Worker

- [x] 2.1 `infra/cloudflare/invite-mail/`: pass `html` to `send`

## 3. Verify

- [x] 3.1 `clojure -M:test`
- [x] 3.2 Mail and page rendered in Chrome, light and dark, phone and desktop
