(ns client.pages.home-test
  "Suggestion-list lifecycle on the home add-word form (GH-178): typing keeps
   the previous list until the dictionary answers; the answer replaces it;
   emptying the input and submitting still clear it."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [application]
   [cljs.test :refer-macros [deftest is testing]]
   [clojure.string :as str]
   [nexus.registry :as nxr]
   [pages.home.actions]
   [pages.home.effects]
   [pages.home.presenter :as presenter]
   [use-cases.vocabulary :as vocabulary]))


;; Minimal nexus bootstrap over `application`'s registrations: the test
;; replaces its state fn, capability injection and batched :effect/save with
;; plain unbatched ones.


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


;; `application`'s own reach for a DOM event the test does not have; these
;; stay inert.
(nxr/register-effect! :effect/prevent-default
  (fn [_ _]))


(nxr/register-effect! :effect/scroll-nearest
  (fn [_ _ _]))


(def hund
  {:lemma "Hund" :translations ["пёс" "собака"] :exact? true})


(def hut
  {:lemma "Hut" :translations ["шляпа"] :exact? true})


(def haus
  {:lemma "das Haus" :pos "noun" :translations ["дом"] :exact? true})


(def ohne
  "One stored translation, `без того, чтобы`, arriving as two padded pieces —
   the shape the adapter produced before #362 made translations elements. Kept
   split on purpose: it is what pins the trim-and-rejoin in `prefill-text`."
  {:lemma "ohne" :translations ["без того" " чтобы"] :exact? true})


(defn- test-system
  "System whose stub dictionary answers from `completions-by-prefix`;
   unknown prefixes (including the empty one) answer [], like the adapter."
  [completions-by-prefix]
  {:store        (atom {})
   :capabilities {:collections {:collections/active-id (fn [] nil)}
                  :dictionary  {:dictionary/completions (fn [prefix]
                                                          (js/Promise.resolve
                                                           (get completions-by-prefix prefix [])))}}})


(defn- suggestion-items
  [store]
  (get-in @store [:home/suggestions :suggestions/items]))


(defn- form-legend
  "What the form says it is about to save — the only place the mode shows."
  [store]
  (get-in (presenter/page-props @store) [:form :legend]))


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
    (with-redefs [vocabulary/add! (fn [_ _ _ _]
                                    (js/Promise.resolve {:word-id "w1" :created? true}))]
      (let [{:keys [store] :as system} (test-system {})]
        (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value nil}]])
        (nxr/dispatch system {} [[:action/add-word {:value "Hund" :translation "пёс"}]])
        (await (debounce-elapsed))
        (is (nil? (:home/suggestions @store)))
        (is (= "" (:home/word @store)))
        (is (= "" (:home/translation @store)))))))


(defn- ^:async error-after-failed-add
  "The store after an add whose save rejected with `err`."
  [err]
  (with-redefs [vocabulary/add! (fn [_ _ _ _] (js/Promise.reject err))]
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-word "Hund"]])
      (nxr/dispatch system {} [[:action/update-translation "пёс"]])
      (nxr/dispatch system {} [[:action/add-word {:value "Hund" :translation "пёс"}]])
      (await (debounce-elapsed))
      @store)))


(deftest a-failed-save-says-so-and-keeps-the-input
  (async-testing "GH-313: a save that threw is said on the form; the input stays"
    (let [state (await (error-after-failed-add (js/Error. "database failed to open")))
          form  (:form (presenter/page-props state))]
      (is (= :save-failed (:home/add-error state)))
      (is (= "Hund" (:home/word state)))
      (is (= "пёс" (:home/translation state)))
      (is (= "Слово не сохранилось: в приложении сбой, и это не ваша ошибка." (:error-text form)))
      (is (false? (:translation-invalid? form))))))


(deftest every-add-error-has-text
  (testing "an empty translation marks the field and says what is missing"
    (let [form (:form (presenter/page-props {:home/add-error :empty-translations}))]
      (is (= "Добавьте перевод." (:error-text form)))
      (is (true? (:translation-invalid? form)))))
  (testing "a phrase that failed to save is named as a phrase"
    (let [state {:home/add-error     :save-failed
                 :home/mode-override {:mode :phrase :value "ab und zu"}
                 :home/word          "ab und zu"}]
      (is (= "Фраза не сохранилась: в приложении сбой, и это не ваша ошибка."
             (get-in (presenter/page-props state) [:form :error-text])))))
  (testing "no error, no text"
    (is (nil? (get-in (presenter/page-props {}) [:form :error-text])))))


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


(deftest typing-on-from-a-picked-word-switches-to-phrase-mode
  (testing "GH-358: the pick decides for its own lemma, the value decides after an edit"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/select-suggestion (assoc haus :focus-id nil)]])
      (is (= "Добавить слово" (form-legend store)))
      (nxr/dispatch system {} [[:action/update-word "das Haus ist gross"]])
      (is (= "Добавить фразу" (form-legend store))))))


(deftest typing-on-from-a-picked-word-is-saved-as-a-phrase
  (async-testing "GH-358: the submitted document follows the re-evaluated mode"
    (let [saved-as (atom nil)]
      (with-redefs [vocabulary/add! (fn [_ _ _ kind]
                                      (reset! saved-as kind)
                                      (js/Promise.resolve {:word-id "e1" :created? true}))]
        (let [system (test-system {})]
          (nxr/dispatch system {} [[:action/select-suggestion (assoc haus :focus-id nil)]])
          (nxr/dispatch system {} [[:action/update-word "das Haus ist gross"]])
          (nxr/dispatch system
                        {}
                        [[:action/add-word
                          {:value       "das Haus ist gross"
                           :translation "дом большой"}]])
          (await (debounce-elapsed))
          (is (= :phrase @saved-as)))))))


(deftest stale-answer-is-ignored
  (testing "an answer queried for an outdated value neither shows nor prefills"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:effect/save {:home/word "auf jeden"}]])
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund] :value "auf"}]])
      (is (nil? (suggestion-items store)))
      (is (nil? (:home/translation @store))))))


(deftest a-prefill-hands-over-the-stored-text
  (async-testing "the form invents no separator: what it shows is stored as one translation (GH-365)"
    (let [{:keys [store] :as system} (test-system {"ohne" [ohne]})]
      (nxr/dispatch system {} [[:action/update-word "ohne"]])
      (await (debounce-elapsed))
      (is (= "без того, чтобы" (:home/translation @store))))))


(deftest a-picked-suggestion-hands-over-the-stored-text
  (testing "picking the entry fills the field with the same text"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/select-suggestion (assoc ohne :focus-id nil)]])
      (is (= "без того, чтобы" (:home/translation @store))))))


(deftest an-empty-answer-leaves-the-translation-blank
  (testing "no suggestions, nothing to fill"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [] :value nil}]])
      (is (str/blank? (:home/translation @store))))))


(defn- active-lemmas
  "The lemmas the props mark active. The view renders `data-active` from
   `:active?` and compares nothing, so this is the whole highlight."
  [store]
  (->> (get-in (presenter/page-props @store) [:form :suggestions :items])
       (filter :active?)
       (mapv :lemma)))


(deftest a-fresh-list-marks-its-first-entry
  (testing "GH-412: the entry Enter would pick is the one the list shows marked"
    (let [{:keys [store] :as system} (test-system {})]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund hut haus] :value nil}]])
      (is (= ["Hund"] (active-lemmas store))))))


(deftest the-arrows-move-the-marked-entry
  (testing "GH-412: exactly one entry is marked, and it is the one the index names"
    (let [{:keys [store] :as system} (test-system {})
          arrow (fn [key]
                  (nxr/dispatch system
                                {}
                                [[:action/handler-word-keydown {:key key}]]))]
      (nxr/dispatch system {} [[:action/update-suggestions {:completions [hund hut haus] :value nil}]])
      (arrow "ArrowDown")
      (is (= ["Hut"] (active-lemmas store)))
      (arrow "ArrowDown")
      (is (= ["das Haus"] (active-lemmas store)))
      ;; The bottom holds: there is no fourth entry to move onto.
      (arrow "ArrowDown")
      (is (= ["das Haus"] (active-lemmas store)))
      (arrow "ArrowUp")
      (is (= ["Hut"] (active-lemmas store)))
      (arrow "ArrowUp")
      (is (= ["Hund"] (active-lemmas store)))
      ;; And so does the top.
      (arrow "ArrowUp")
      (is (= ["Hund"] (active-lemmas store))))))


;; The rename in the heading (#460). What the screen on display shows of it
;; follows memory, which takes the renamed collection when PouchDB does; the
;; effect only writes, or refuses to.


(defn- rename-system
  [writes]
  {:store        (atom {})
   :capabilities {:collections
                  {:collections/active-id (fn [] "c:kurs")
                   :collections/get       (fn [_] (js/Promise.resolve {:id "c:kurs" :name "Kurs"}))
                   :collections/list      (fn []
                                            (js/Promise.resolve [{:id "c:kurs" :name "Kurs"}
                                                                 {:id "c:gram" :name "Grammatik"}]))
                   :collections/rename!   (fn [id name]
                                            (swap! writes conj [id name])
                                            (js/Promise.resolve nil))}}})


(defn- settled
  "Resolves once every promise the effect chained has run."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 0))))


(deftest a-rename-writes-once-and-a-taken-name-writes-nothing
  (async-testing "GH-460: the rename is written; a name another collection carries is not"
    (let [writes (atom [])
          system (rename-system writes)]
      (nxr/dispatch system {} [[:effect/rename-active-collection " Neu "]])
      (await (settled))
      (is (= [["c:kurs" "Neu"]] @writes))
      (nxr/dispatch system {} [[:effect/rename-active-collection "grammatik"]])
      (await (settled))
      (is (= [["c:kurs" "Neu"]] @writes)))))
