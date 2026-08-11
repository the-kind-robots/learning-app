# Tasks: user-phrases

## 1. Entity foundation

- [ ] 1.1 `domain/phrase.cljs`: phrase id (`phrase:<normalize-german>`), new-phrase doc constructor (single-entry translation, collapsed whitespace), phrase predicate
- [ ] 1.2 Register `"phrase"` → `:user/db` in the doc-type→db map (`db/pouch.cljs`)
- [ ] 1.3 `use_cases/phrase.cljs` `add!`: dedup in phrase namespace, whole-entry translation merge, initial review seed, active-collection membership, no example request
- [ ] 1.4 Progress-store union queries: `list-words`, `count-words`, `empty-vocab?`, `find-word-by-value`/`find-duplicate`, lesson selection — vocab ∪ phrase via two `find-all` (no `$in`)
- [ ] 1.5 Conflict resolver (`sync.cljs`): add `phrase:` range pass with translation union, entries kept whole
- [ ] 1.6 Import LWW (`data_export.cljs`): admit `"phrase"` type
- [ ] 1.7 Unit tests: phrase id/doc, `add!` (dedup, merge without split, no example task), union queries

## 2. Dictionary completions

- [ ] 2.1 `adapters/dictionary.cljs` + `ports/dictionary.cljs`: keep `:pos` in completion maps

## 3. Home form

- [ ] 3.1 Autogrow textarea for both fields: rows=1, `field-sizing: content` + scrollHeight JS fallback, max-height + inner scroll, ≥16px font, existing input attrs
- [ ] 3.2 Mode detection in presenter: space heuristic with article/`sich`/non-phrase-lemma exceptions; suggestion pos override; paste with spaces → phrase; state `:home/add-mode` + override
- [ ] 3.3 Phrase-mode mechanics: clear open suggestions on mode flip, guard stale async completion responses against current input
- [ ] 3.4 Chip UI: appears on detected phrase mode, tappable override until form clear; phrase-mode layout (vertical, full width), copy (legend/labels/placeholders)
- [ ] 3.5 Suggestion badge for multi-word pos=phrase items; selecting one sets phrase mode and fills fields
- [ ] 3.6 Submit routing: phrase mode → `phrase/add!`; cross-type duplicate hint «Уже есть как слово» (non-blocking); success clears form+override, refocuses
- [ ] 3.7 Enter behavior: German field → focus translation; translation → submit; line breaks collapse to spaces on submit

## 4. Words page

- [ ] 4.1 Presenter emits `:phrase?`; row badge + multi-line wrap CSS (no ellipsis)
- [ ] 4.2 Edit dialog: phrase translation as whole multi-line string (bypass `parse-translations`), same autogrow component; delete path verified for phrase ids

## 5. Lesson

- [ ] 5.1 `normalize-for-grading` in shared utils: existing folding + apostrophes (U+0027/U+2019/U+02BC) removed, unicode quotes/dashes → space; unit tests
- [ ] 5.2 Trial generation: phrase trial type (prompt = translation, answer = value), no example trials for phrases; instruction copy «Переведите фразу на немецкий»
- [ ] 5.3 Grading branch for phrase trials via `normalize-for-grading`; reviews written to phrase log
- [ ] 5.4 Token diff domain fn (LCS over normalized tokens, match/wrong/extra/missing) + unit tests
- [ ] 5.5 Error footer for phrase trials: diff-highlighted answer and reference; «ИСПРАВИТЬ» (restores preserved answer, cursor at end) + «ПОЗЖЕ»; autogrow answer textarea
- [ ] 5.6 `:reviewed-trial-ids` in lesson state: first graded attempt per trial per lesson writes the review; later attempts skip

## 6. ADR

- [ ] 6.1 ADR 0011: extend content-addressing contract (ADR-0008) to the `phrase:` namespace — references, not supersedes

## 7. Verification

- [ ] 7.1 Run client unit test suite (shadow-cljs node tests)
- [ ] 7.2 Browser pass (learning-app-cdp): add phrase (auto mode, exceptions, chip, autogrow), words list badge/wrap, phrase lesson trial (diff, retry, single review), duplicates, pos=phrase suggestion
- [ ] 7.3 Sync check: phrase replicates to CouchDB; conflicting translations union (main stand)
- [ ] 7.4 Screenshots for the PR (test data only)
