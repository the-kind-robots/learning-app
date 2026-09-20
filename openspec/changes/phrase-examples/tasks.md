## 1. Backend: accept a phrase as the generation target

- [ ] 1.1 In `src/backend/examples/dictionary.clj`, decide the target shape once: a multi-word
      target that is not an article-plus-noun or `sich`-plus-verb is a phrase. Check what
      `has-article?` and `same-lemma?` do with `das heißt` today — `has-article?` matches it, so
      `same-lemma?` demands an exact string match and the article-stripping path is never reached.
- [ ] 1.2 Make `lemma-in-structure?` accept a phrase target: satisfied when a `structure` item's
      `dictionaryForm` is the whole phrase, compared under the same normalization words use. Keep
      the word path byte-identical in behaviour, homographs included (`der Leiter` vs `die Leiter`).
- [ ] 1.3 Leave `lookup-word-meta` unchanged in shape; confirm a phrase target that finds no
      dictionary entry falls through to the existing `nil` path (cefrLevel default) rather than
      erroring.
- [ ] 1.4 In `src/backend/examples.clj`, replace `sentence-length-ok?`'s flat 3–12 with a bound
      that floats with the target: target word count to target + ~6, never narrower than 3–12.
- [ ] 1.5 Rewrite `system-prompt`: one target concept ("lemma or phrase"), rules grouped by what
      they govern, few-shot cut to one case per distinct shape — noun with article, separable verb,
      reflexive, homograph, phrase. Few-shot stays in code.
- [ ] 1.6 Rewrite `issue-messages` in the same pass so each retry message states the rule in the
      same words the prompt states it in, and add the phrase case to `:target-lemma-missing`.
- [ ] 1.7 Confirm `add-word-indexes` handles a phrase whose words are separated in the sentence
      (`auf jeden Fall` in `Ich komme auf jeden Fall mit.`) and that the duplicate-pair guard does
      not reject a legitimate repeated word of the phrase.

## 2. Client: the add flow queues the fetch

- [ ] 2.1 In `src/client/use_cases/phrase.cljs`, queue the example fetch the way
      `use_cases/vocabulary.cljs` does — collection id and name resolved the same way, the
      `:examples/request!` call in both the created and the existing-entry branch, including the
      "no example for this (entry, collection) pair yet" check.
- [ ] 2.2 Delete the docstring claim that a phrase needs no example; the comment must not outlive
      the decision it recorded.
- [ ] 2.3 Keep the "translation is never split on punctuation" rule untouched: it is the one
      phrase-specific thing in this use case and nothing here should reach it.
- [ ] 2.4 Check the capabilities map the phrase use case is constructed with actually carries
      `:examples`; add it at the construction site if it does not.

## 3. Client: the lesson treats a phrase like a word

- [ ] 3.1 In `src/client/domain/lesson.cljs`, generate example trials for a phrase's examples, not
      only a word's.
- [ ] 3.2 Change the `unlock-example-trials` trigger from `word-trial?` to "any vocabulary trial",
      so a correct phrase answer unlocks that phrase's example trials.
- [ ] 3.3 Confirm `answer-segments` needs no change: each word of the phrase has its own
      `wordIndex`, so the annotation path is the one words already use.
- [ ] 3.4 Confirm an example trial answer still writes no vocabulary review, whichever kind of
      entry it belongs to.

## 4. Tests

- [ ] 4.1 `test/backend/examples_test.clj`: a phrase target validates when `structure` carries one
      item per word with the whole phrase as `dictionaryForm`; rejects when the construction is
      missing or partially present; the split case (`auf jeden Fall`) indexes correctly; the
      floating length bound accepts a long phrase and still rejects a two-word sentence for a word.
- [ ] 4.2 `test/backend/examples_test.clj`: the existing word cases — homograph, separable verb,
      reflexive, article — still pass unchanged against the rewritten prompt's validation.
- [ ] 4.3 `test/client/phrase_test.cljs`: adding a phrase queues an example-fetch task with the
      active collection context; re-adding into a collection that already has one queues nothing;
      translation is still stored whole.
- [ ] 4.4 `test/client/domain/lesson_test.cljs`: a phrase with an example produces a phrase trial
      plus a locked example trial; a correct phrase answer unlocks it; an incorrect one does not.
- [ ] 4.5 `test/client/examples_test.cljs`: the fetch/store path is kind-agnostic.

## 5. ADR amendment

- [ ] 5.1 Amend `adr/0011-phrases-share-the-vocabulary-namespace.md` in the style ADR-0002 uses for
      the point ADR-0012 superseded: strike the "no LLM example is requested for a phrase" bullet
      and follow it with a note that it no longer holds, naming #371 as the reversal. Add the
      corresponding header line to the ADR's metadata block.
- [ ] 5.2 The note describes and points; it SHALL NOT restate the new behaviour. The delta spec and
      the synced main spec are the only normative places.

## 6. Verification

- [ ] 6.1 Run the ClojureScript and Clojure test suites.
- [ ] 6.2 Live provider run, before/after on a fixed list of ~12 words and ~8 phrases, counting how
      many generations pass validation on the first try. Load the key with
      `set -a; . ~/.config/environment.d/99-llm.conf; set +a`; the dictionary lookup needs the
      shared CouchDB `dictionary-db`, so this runs from the main checkout. Record both numbers on
      #371 — the point is showing the rewritten prompt did not degrade word examples.
- [ ] 6.3 Keep the list fixed and small: every call costs money.
- [ ] 6.4 Browser check: add a phrase, confirm the example arrives and that a lesson offers its
      example trial only after the phrase trial is answered correctly.
- [ ] 6.5 Archive the change on the branch before opening the PR.
