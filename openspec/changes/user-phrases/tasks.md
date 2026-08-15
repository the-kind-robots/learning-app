# Tasks: user-phrases

## 1. Entity foundation

- [x] 1.1 `domain/phrase.cljs`: new-phrase doc constructor on the vocab id (single-entry translation, collapsed whitespace, `kind`), phrase predicate reading `kind`
- [x] 1.2 No new doc type: storage routing, replication and export stay as they are
- [x] 1.3 `use_cases/phrase.cljs` `add!`: dedup by value, whole-entry translation merge that leaves the kind alone, initial review seed, active-collection membership, no example request
- [x] 1.4 Progress-store queries unchanged: one `vocab` query already answers for both kinds
- [x] 1.5 Conflict resolver (`sync.cljs`): phrases resolve in the existing `vocab:` pass, entries kept whole
- [x] 1.6 Import LWW (`data_export.cljs`): unchanged, phrases arrive as vocab documents
- [x] 1.7 Unit tests: phrase doc shape and kind predicate, `add!` (dedup, merge without split, no example task)

## 2. Dictionary completions

- [x] 2.1 `adapters/dictionary.cljs` + `ports/dictionary.cljs`: keep `:pos` in completion maps

## 3. Home form

- [x] 3.1 Autogrow textarea for both fields: rows=1, `field-sizing: content` + scrollHeight JS fallback, max-height + inner scroll, ≥16px font, existing input attrs
- [x] 3.2 Mode detection in presenter: space heuristic with article/`sich`/non-phrase-lemma exceptions; suggestion pos override; paste with spaces → phrase; state `:home/add-mode` + override
- [x] 3.3 Phrase-mode mechanics: clear open suggestions on mode flip, guard stale async completion responses against current input
- [x] 3.4 Chip UI: appears on detected phrase mode, tappable override until form clear; phrase-mode layout (vertical, full width), copy (legend/labels/placeholders)
- [x] 3.5 Suggestion badge for multi-word pos=phrase items; selecting one sets phrase mode and fills fields
- [x] 3.6 Submit routing: phrase mode → `phrase/add!`; a value that also exists as a word is created without a warning; success clears form+override, refocuses
- [x] 3.7 Enter behavior: German field → focus translation; translation → submit; line breaks collapse to spaces on submit

## 4. Words page

- [x] 4.1 Presenter emits `:phrase?`; multi-line wrap CSS (no ellipsis), no type label in the row
- [x] 4.2 Edit dialog: phrase translation as whole multi-line string (bypass `parse-translations`), same autogrow component; delete path verified for phrase ids

## 5. Lesson

- [x] 5.1 `normalize-for-grading` in shared utils: existing folding + apostrophes (U+0027/U+2019/U+02BC) removed, unicode quotes/dashes → space; unit tests
- [x] 5.2 Trial generation: phrase trial type (prompt = translation, answer = value), no example trials for phrases; instruction copy «Переведите фразу на немецкий»
- [x] 5.3 Grading branch for phrase trials via `normalize-for-grading`; reviews written to phrase log
- [x] 5.4 Error footer untouched for phrase trials; autogrow answer textarea
- [x] 5.5 Reviews written per graded attempt for phrase trials, as for words; example trials still write none

## 6. ADR

- [x] 6.1 ADR 0011: phrases share the vocabulary namespace and carry their kind — references ADR-0008, not supersedes

## 7. Verification

- [x] 7.1 Run client unit test suite (shadow-cljs node tests)
- [x] 7.2 Browser pass (learning-app-cdp): add phrase (auto mode, exceptions, chip, autogrow), words list wrap, phrase lesson trial, duplicates, pos=phrase suggestion
- [x] 7.3 Sync check: phrase replicates to CouchDB; conflicting translations union (main stand)
- [x] 7.4 Screenshots for the PR (test data only)
