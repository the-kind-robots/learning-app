(ns client.pages.home-test
  "Suggestion-list lifecycle on the home add-word form (GH-178): typing keeps
   the previous list until the dictionary answers; the answer replaces it;
   emptying the input and submitting still clear it."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [clojure.string :as str]
   [nexus.registry :as nxr]
   [pages.home.actions]
   [pages.home.effects]
   [use-cases.vocabulary :as vocabulary]))


;; Minimal nexus bootstrap: the production registrations for these live in
;; `application` (browser-only requires), so the test registers its own
;; state fn, capability injection and a plain unbatched :effect/save.


(nxr/register-system->state!
 (fn [system]
   (deref (:store system))))


(nxr/register-interceptor! :before-effect
  (fn [{:keys [system] :as ctx}]
    (assoc ctx :capabilities (:capabilities system))))


(nxr/register-effect! :effect/save
  (fn [_ system new-state]
    (swap! (:store system) merge new-state)))


(nxr/register-effect! :effect/focus
  (fn [_ _ _]))


(nxr/register-effect! :effect/clear-autogrow
  (fn [_ _ _]))


(def hund
  {:lemma "Hund" :translations ["пёс" "собака"] :exact? true})


(def hut
  {:lemma "Hut" :translations ["шляпа"] :exact? true})


(defn- test-system
  "System whose stub dictionary answers from `completions-by-prefix`;
   unknown prefixes (including the empty one) answer [], like the adapter."
  [completions-by-prefix]
  {:store        (atom {})
   :capabilities {:collections {:collections/active-id (fn [] nil)}
                  :dictionary  {:dictionary/ready?      (fn [] true)
                                :dictionary/completions (fn [prefix]
                                                          (js/Promise.resolve
                                                           (get completions-by-prefix prefix [])))}}})


(defn- suggestion-items
  [store]
  (get-in @store [:home/suggestions :suggestions/items]))


(defn- debounce-elapsed
  "Resolves after the 150 ms suggest! debounce plus slack."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 250))))


(deftest second-keystroke-keeps-suggestions
  (testing "typing another character leaves the previous list in place"
    (let [{:keys [store] :as system} (test-system {"hu" [hund]})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
      (nxr/dispatch system {} [[:action/update-word "hu"]])
      (is (= "hu" (:home/word @store)))
      (is (= [hund] (suggestion-items store))))))


(deftest dictionary-answer-replaces-suggestions
  (async-testing "the debounced dictionary answer replaces the kept list"
    (let [{:keys [store] :as system} (test-system {"hut" [hut]})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
      (nxr/dispatch system {} [[:action/update-word "hut"]])
      (is (= [hund] (suggestion-items store)))
      (await (debounce-elapsed))
      (is (= [hut] (suggestion-items store)))
      (is (zero? (get-in @store [:home/suggestions :suggestions/active-idx]))))))


(deftest emptied-input-clears-suggestions
  (async-testing "an emptied input clears the list through the dictionary's [] answer"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
      (nxr/dispatch system {} [[:action/update-word ""]])
      (await (debounce-elapsed))
      (is (empty? (suggestion-items store))))))


(deftest submit-clears-suggestions
  (async-testing "a successful submit resets the form, suggestions included"
    (with-redefs [vocabulary/add!  (fn [_ _ _]
                                     (js/Promise.resolve {:word-id "w1" :created? true}))
                  vocabulary/count (fn [_]
                                     (js/Promise.resolve 1))]
      (let [{:keys [store] :as system} (test-system {})]
        (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
        (nxr/dispatch system {} [[:action/add-word {:value "Hund" :translation "пёс"}]])
        (await (debounce-elapsed))
        (is (nil? (:home/suggestions @store)))
        (is (= "" (:home/word @store)))
        (is (= "" (:home/translation @store)))))))


(deftest picking-a-kept-suggestion-uses-its-own-data
  (testing "a pick from a list kept across keystrokes fills word and translation from the entry itself"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
      (nxr/dispatch system {} [[:action/update-word "hut"]])
      (nxr/dispatch system {} [[:action/select-suggestion (assoc hund :focus-id nil)]])
      (is (= "Hund" (:home/word @store)))
      (is (= "пёс, собака" (:home/translation @store)))
      (is (nil? (:home/suggestions @store))))))


(deftest arrival-fills-a-blank-translation-from-the-top-suggestion
  (async-testing "the owner's yes in GH-178: the top suggestion pre-fills the empty field"
    (let [{:keys [store] :as system} (test-system {"hund" [hund hut]})]
      (nxr/dispatch system {} [[:action/update-word "hund"]])
      (await (debounce-elapsed))
      (is (= [hund hut] (suggestion-items store)))
      (is (= "пёс, собака" (:home/translation @store))))))


(deftest arrival-never-clobbers-a-typed-translation
  (testing "late dictionary answers do not overwrite the user's own text"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-translation "мой перевод"]])
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
      (is (= "мой перевод" (:home/translation @store))))))


(deftest a-typed-translation-survives-further-typing-in-the-german-field
  (testing "the translation field neither blanks nor refills while the word is typed"
    (let [{:keys [store] :as system} (test-system {"hu" [hund]})]
      (nxr/dispatch system {} [[:action/update-translation "мой перевод"]])
      (nxr/dispatch system {} [[:action/update-word "hu"]])
      (is (= "мой перевод" (:home/translation @store)))
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value "hu"}]])
      (is (= "мой перевод" (:home/translation @store))))))


(deftest a-prefilled-translation-leaves-when-the-input-becomes-a-phrase
  (async-testing "the word's translation does not linger under an unrelated phrase"
    (let [{:keys [store] :as system} (test-system {"hätte" [{:lemma "hätte" :translations ["иметь"]}]})]
      (nxr/dispatch system {} [[:action/update-word "hätte"]])
      (await (debounce-elapsed))
      (is (= "иметь" (:home/translation @store)))
      (nxr/dispatch system {} [[:action/update-word "hätte hätte fahrad kätte"]])
      (await (debounce-elapsed))
      (is (= "" (:home/translation @store))))))


(deftest an-emptied-translation-may-be-prefilled-again
  (async-testing "clearing the field hands it back to the dictionary"
    (let [{:keys [store] :as system} (test-system {"hund" [hund]})]
      (nxr/dispatch system {} [[:action/update-translation "мой перевод"]])
      (nxr/dispatch system {} [[:action/update-translation ""]])
      (nxr/dispatch system {} [[:action/update-word "hund"]])
      (await (debounce-elapsed))
      (is (= "пёс, собака" (:home/translation @store))))))


(deftest a-picked-suggestion-owns-the-translation
  (testing "a later dictionary answer does not overwrite what the pick filled in"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/select-suggestion (assoc hund :focus-id nil)]])
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hut] :value "Hund"}]])
      (is (= "пёс, собака" (:home/translation @store))))))


(deftest stale-answer-is-ignored
  (testing "an answer queried for an outdated value neither shows nor prefills"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:effect/save {:home/word "auf jeden"}]])
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value "auf"}]])
      (is (nil? (suggestion-items store)))
      (is (nil? (:home/translation @store))))))


(deftest an-empty-answer-leaves-the-translation-blank
  (testing "no suggestions, nothing to fill"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [] :value nil}]])
      (is (str/blank? (:home/translation @store))))))
