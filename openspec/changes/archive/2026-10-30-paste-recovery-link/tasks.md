## 1. Tests first

- [x] 1.1 Browser spec titles in `test/browser/sync-control.spec.js`: without an account the menu offers the paste; a pasted link joins the account; a pasted pairing link writes the receipt; a text that is not a link is refused; `npm run test:catalog`

## 2. Client

- [x] 2.1 `adapters.account/open-recovery-link!` and its port: a `#key=` fragment goes into the address and the page reloads; anything else answers false
- [x] 2.2 The no-account sync menu has the paste item after «Сохранить мои слова»; `:effect/paste-recovery-link` asks with `prompt`, refuses with `alert`
- [x] 2.3 Texts in the ru, en, tr and fr dictionaries

## 3. Verify

- [x] 2.4 A device with no identity adopts the token of its auth cookie on boot; the cookie is kept for 400 days
- [x] 3.1 `sync-control.spec.js`, unit tests, clj-kondo
- [ ] 3.2 The installed app on an iPhone joins an account from a pasted link — not provable in the worktree
