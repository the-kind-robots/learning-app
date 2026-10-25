## 1. Tests

- [x] 1.1 Browser spec: the page declares `lang="ru"` and the home screen reads in Russian

## 2. Dictionary

- [x] 2.1 m1p on both classpaths; `i18n` namespace with `language`, the prepared dictionary and `interpolate`, honoring ADR-0025
- [x] 2.2 `src/shared/i18n/ru.edn` with every interface string
- [x] 2.3 `interpolate` copies only the branches that hold text

## 3. Views

- [x] 3.1 Shell, sync, install guide, home, words, lesson and collections views write `[:i18n …]`
- [x] 3.2 `render!` interpolates the shell; the server's layout interpolates its body and sets `<html lang>`

## 4. Verify

- [x] 4.1 Browser suite passes, desktop and mobile
- [x] 4.2 Interpolation cost on a long word list measured
