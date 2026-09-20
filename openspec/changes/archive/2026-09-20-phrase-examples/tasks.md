## 1. Backend: accept a phrase as the generation target

- [x] 1.1 In `src/backend/examples/dictionary.clj`, decide the target shape once: a multi-word
      target that is not an article-plus-noun or `sich`-plus-verb is a phrase, with the
      dictionary's own part of speech settling `das heißt`, which reads as an article pair.
      `phrase-target?` is the one place that decides it.
- [x] 1.2 `lemma-in-structure?` needs no change, which a run settled rather than a reading: both
      branches of `same-lemma?` compare a multi-word target as a whole string, since the stripping
      only ever touches a leading article or `sich`. `das heißt`, `auf jeden Fall`,
      `den Kopf verlieren` and `von Zeit zu Zeit` all match already, and `die Leiter` still does
      not match `der Leiter`. Only the docstring changed.
- [x] 1.3 Leave `lookup-word-meta` unchanged in shape; confirm a phrase target that finds no
      dictionary entry falls through to the existing `nil` path (cefrLevel default) rather than
      erroring.
- [x] 1.4 In `src/backend/examples.clj`, replace `sentence-length-ok?`'s flat 3–12 with a bound
      that floats with the target: target word count to target + ~6, never narrower than 3–12.
- [x] 1.5 Rewrite `system-prompt`: one target concept ("lemma or phrase"), rules grouped by what
      they govern, few-shot cut to one case per distinct shape — noun with article, separable verb,
      reflexive, homograph, phrase. Few-shot stays in code.
- [x] 1.6 Rewrite `issue-messages` in the same pass so each retry message states the rule in the
      same words the prompt states it in, and add the phrase case to `:target-lemma-missing`.
- [x] 1.7 Confirm `add-word-indexes` handles a phrase whose words are separated in the sentence
      (`auf jeden Fall` in `Ich komme auf jeden Fall mit.`).
- [x] 1.8 Narrow `structure-has-duplicate-items?` instead of deleting it. A repeat is legitimate
      when the repeated word is a word of the phrase target (`von Zeit zu Zeit`, `nach und nach`,
      `Schritt für Schritt`, `Zug um Zug`); it stays a rejection otherwise, which is what stops a
      separable verb annotating the preposition that shares its prefix's spelling
      (`Pass auf deine Sachen auf!`). Decide which shape survives both cases by running them, not
      by reading the code.

## 2. Client: one add flow for both kinds

- [x] 2.1 Fold `use-cases.phrase/add!` and `use-cases.vocabulary/add!` into one `add!` in
      `use-cases.vocabulary`, parameterised by the kind. Once the example fetch is common, the only
      differences left are the translation (`domain.vocabulary/parse-translations` splits, a
      phrase's stays one whole entry) and the document the kind builds (`new-word` vs
      `new-phrase`). Duplicate lookup, merge, initial review, collection membership and the
      re-fetch rule for an existing entry are already the same code twice.
- [x] 2.2 `use-cases.phrase/add!` ends up either gone, with `pages/home/effects.cljs` passing the
      mode, or a two-line wrapper — whichever reads better against the repo's style.
      `domain.phrase` stays as it is.
- [x] 2.3 Delete the docstring claim that a phrase needs no example; the comment must not outlive
      the decision it recorded.
- [x] 2.4 Keep the "translation is never split on punctuation" rule intact through the merge: it is
      the one phrase-specific thing in the flow.
- [x] 2.5 Check the capabilities map the phrase path is constructed with actually carries
      `:examples`; add it at the construction site if it does not.
- [x] 2.6 This unification changes no behaviour, so it carries no delta. The behaviour that does
      change — the queued fetch — is stated once, in `specs/user-phrases/spec.md`.

## 3. Client: the lesson treats a phrase like a word

- [x] 3.1 In `src/client/domain/lesson.cljs`, generate example trials for a phrase's examples, not
      only a word's.
- [x] 3.2 Change the `unlock-example-trials` trigger from `word-trial?` to "any vocabulary trial",
      so a correct phrase answer unlocks that phrase's example trials.
- [x] 3.3 Confirm `answer-segments` needs no change: each word of the phrase has its own
      `wordIndex`, so the annotation path is the one words already use.
- [x] 3.4 Confirm an example trial answer still writes no vocabulary review, whichever kind of
      entry it belongs to.

## 4. Tests

- [x] 4.1 `test/backend/examples_test.clj`: a phrase target validates when `structure` carries one
      item per word with the whole phrase as `dictionaryForm`; rejects when the construction is
      missing or partially present; the split case (`auf jeden Fall`) indexes correctly; the
      floating length bound accepts a long phrase and still rejects a two-word sentence for a word.
- [x] 4.2 `test/backend/examples_test.clj`: the existing word cases — homograph, separable verb,
      reflexive, article — still pass unchanged against the rewritten prompt's validation.
- [x] 4.3 `test/client/phrase_test.cljs`: adding a phrase queues an example-fetch task with the
      active collection context; re-adding into a collection that already has one queues nothing;
      translation is still stored whole.
- [x] 4.4 `test/client/domain/lesson_test.cljs`: a phrase with an example produces a phrase trial
      plus a locked example trial; a correct phrase answer unlocks it; an incorrect one does not.
- [x] 4.5 `test/client/examples_test.cljs`: the fetch/store path is kind-agnostic.

## 5. ADR amendment

- [x] 5.1 Amend `adr/0011-phrases-share-the-vocabulary-namespace.md` in the style ADR-0002 uses for
      the point ADR-0012 superseded: strike the "no LLM example is requested for a phrase" bullet
      and follow it with a note that it no longer holds, naming #371 as the reversal. Add the
      corresponding header line to the ADR's metadata block.
- [x] 5.2 The note describes and points; it SHALL NOT restate the new behaviour. The delta spec and
      the synced main spec are the only normative places.

## 6. Verification

- [x] 6.1 Run the ClojureScript and Clojure test suites.
- [x] 6.2 Live provider run from this worktree, before/after on a fixed list of ~12 words and ~8
      phrases, counting how many generations pass validation on the first try. Load the key with
      `set -a; . ~/.config/environment.d/99-llm.conf; set +a`. CouchDB is reached over HTTP and a
      worktree shares the runtime, so `dictionary-db` needs nothing the main checkout has. Record
      word and phrase counts separately on #371 — the word numbers are what show the rewritten
      prompt cost nothing.
- [x] 6.3 Keep the list fixed and small: every call costs money. Include the repeated-word phrases
      (`von Zeit zu Zeit`, `nach und nach`) and a split construction (`auf jeden Fall`), and record
      what they actually generated, not what the code suggests they would.
- [ ] 6.4 Browser check: add a phrase, confirm the example arrives and that a lesson offers its
      example trial only after the phrase trial is answered correctly.
- [x] 6.5 Archive the change on the branch before opening the PR.
