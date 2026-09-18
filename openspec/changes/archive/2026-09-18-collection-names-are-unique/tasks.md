## 1. Use case

- [x] 1.1 `rename-active!` refuses a name another collection carries by `domain.collections/same-name?`, excluding itself by id; returns `{:name current :noop :duplicate}`
- [x] 1.2 Node test: taken by case and whitespace, blank, own name in another case, free name, no active collection

## 2. Verification

- [x] 2.1 Tiles browser spec: renaming `Kurs` to ` kurs / kapitel 1 ` reverts the heading and leaves one document per name, one tile per name
