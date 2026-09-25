(ns client.pages.words-effects-test
  "How the word list behaves while several reads of it are in flight (GH-439):
   which answer is allowed to write, what a page arriving in the background
   does to an open word, and when the list goes back to its first row."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is]]
   [nexus.registry :as nxr]
   [pages.words.actions]
   [pages.words.effects]
   [pages.words.presenter :as presenter]
   [use-cases.vocabulary :as vocabulary]))


;; Minimal nexus bootstrap, as in `client.pages.home-test`: the production
;; registrations live in `application`, which is browser-only.


(nxr/register-system->state!
 (fn [system]
   (deref (:store system))))


(nxr/register-interceptor! :before-effect
  (fn [{:keys [system] :as ctx}]
    (assoc ctx :capabilities (:capabilities system))))


(nxr/register-effect! :effect/save
  (fn [_ system new-state]
    (swap! (:store system) merge new-state)))


(def ^:private scrolls
  "How many times the list was asked to go back to its first row. The real
   effect reaches for `document`, which node has not got."
  (atom 0))


(nxr/register-effect! :effect/scroll-words-to-top
  (fn [_ _]
    (swap! scrolls inc)))


;; -----------------------------------------------------------------------------
;; A list read that answers when the test says so
;; -----------------------------------------------------------------------------


(def ^:private unanswered
  "Reads asked for and not yet answered, keyed by [search limit]."
  (atom {}))


(defn- list-stub
  [_ {:keys [limit search]}]
  (js/Promise. (fn [resolve _]
                 (swap! unanswered assoc [search limit] resolve))))


(defn- word-rows
  [n]
  (vec (for [i (range n)]
         {:id (str "word-" i) :value (str "Wort" i) :translation [] :retention-level 0})))


(defn- answer!
  "Answers the read asked for under [search limit] with `n` rows out of
   `matches` matching ones."
  [[search limit] n matches]
  (let [resolve (get @unanswered [search limit])]
    (is (some? resolve) (str "no read outstanding for " (pr-str [search limit])))
    (swap! unanswered dissoc [search limit])
    (resolve {:matches matches :total 130 :words (word-rows n)})))


(defn- settled
  "Resolves once the promise chain the answer started has run out."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 0))))


(defn- debounce-elapsed
  "Resolves after the 400 ms search debounce plus slack."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 500))))


(defn- test-system
  [& [state]]
  {:store        (atom (or state {}))
   :capabilities {}})


;; -----------------------------------------------------------------------------


(deftest a-page-answered-after-a-search-does-not-write
  (async-testing "the unfiltered page loses to the query that overtook it"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [{:keys [store] :as system} (test-system)]
        (nxr/dispatch system {} [[:action/load-words {:limit 100 :search ""}]])
        (nxr/dispatch system {} [[:action/search-words "wort1"]])
        (await (debounce-elapsed))
        (answer! ["wort1" presenter/page-size] 11 11)
        (await (settled))
        (is (= "wort1" (:words/search @store)))
        (is (= 11 (count (:words/items @store))))

        (answer! ["" 100] 100 130)
        (await (settled))
        (is (= "wort1" (:words/search @store))
            "the late page left the query the reader typed alone")
        (is (= 11 (count (:words/items @store)))
            "and left the rows it matched alone")))))


(deftest a-page-is-not-asked-for-while-a-search-is-pending
  (async-testing "reaching the end during the 400 ms asks for the old query's page"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [system (test-system {:words/more? true :words/limit 50 :words/search ""})]
        (nxr/dispatch system {} [[:action/search-words "wort1"]])
        (nxr/dispatch system {} [[:action/show-more-words]])
        (await (settled))
        (is (nil? (get @unanswered ["" 100]))
            "no page of the query that is being replaced")
        (await (debounce-elapsed))
        (answer! ["wort1" presenter/page-size] 11 11)
        (await (settled))))))


(deftest rows-arriving-leave-an-open-word-open
  (async-testing "a page landing in the background does not shut the dialog"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [{:keys [store] :as system} (test-system)]
        (nxr/dispatch system {} [[:action/load-words {:limit 100 :search ""}]])
        (await (settled))
        (nxr/dispatch system {} [[:action/open-word-edit {:id "word-3" :value "Wort3"}]])
        (answer! ["" 100] 100 130)
        (await (settled))
        (is (= "word-3" (:id (:words/editing @store)))
            "the word the reader opened is still open")
        (is (= 100 (count (:words/items @store)))
            "and the rows did arrive")))))


(deftest saving-closes-the-open-word
  (async-testing "the dialog closes on the save, not on the rows it brings back"
    (with-redefs [vocabulary/list-active list-stub
                  vocabulary/update!     (fn [& _] (js/Promise.resolve nil))]
      (reset! unanswered {})
      (let [{:keys [store] :as system} (test-system {:words/limit 50 :words/search ""})]
        (nxr/dispatch system {} [[:action/open-word-edit {:id "word-3" :value "Wort3"}]])
        (nxr/dispatch system {} [[:action/save-word {:id "word-3" :translation "перевод"}]])
        (is (nil? (:words/editing @store)))
        (await (settled))
        (answer! ["" 50] 50 130)
        (await (settled))
        (is (nil? (:words/editing @store)))))))


(deftest the-screen-reads-its-first-page-on-entry
  (async-testing "the route controller asks with no options and gets a first page"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [{:keys [store] :as system} (test-system)]
        (nxr/dispatch system {} [[:action/load-words]])
        (await (settled))
        (is (some? (:words/current-read @store)) "the entry read is running")
        (answer! [nil presenter/page-size] presenter/page-size 130)
        (await (settled))
        (is (= presenter/page-size (count (:words/items @store))))
        (is (nil? (:words/current-read @store)) "and has answered")))))


(deftest no-page-read-while-a-read-is-running
  (async-testing "the observer firing twice before the page lands asks once"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [{:keys [store] :as system}
            (test-system {:words/more? true :words/limit 50 :words/search ""})]
        (nxr/dispatch system {} [[:action/show-more-words]])
        (let [read (:words/current-read @store)]
          (nxr/dispatch system {} [[:action/show-more-words]])
          (is (= read (:words/current-read @store)) "the second fire started nothing"))
        (await (settled))
        (answer! ["" 100] 100 130)
        (await (settled))
        (is (= 100 (count (:words/items @store))))
        (is (nil? (:words/current-read @store)))))))


(deftest a-failed-read-is-no-longer-running
  (async-testing "an error ends the read, so the next page can be asked for"
    (with-redefs [vocabulary/list-active (fn [& _] (js/Promise.reject (js/Error. "boom")))]
      (let [{:keys [store] :as system}
            (test-system {:words/more? true :words/limit 50 :words/search ""})]
        (nxr/dispatch system {} [[:action/show-more-words]])
        (await (settled))
        (is (nil? (:words/current-read @store)))))))


(deftest a-cancelled-read-failing-leaves-the-current-one-running
  (let [store (atom {:words/current-read "newer"})]
    (nxr/dispatch {:store store} {} [[:action/words-read-ended "older"]])
    (is (= "newer" (:words/current-read @store)))))


(deftest the-reload-a-pull-triggers-is-numbered-when-it-happens
  (async-testing "the reload built from the rows on screen lands, rather than carrying a spent number"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (let [{:keys [store] :as system} (test-system)]
        (nxr/dispatch system {} [[:action/load-words {:limit 100 :search "wort1"}]])
        (await (settled))
        (answer! ["wort1" 100] 11 11)
        (await (settled))
        ;; What `:action/reload-page` runs on this screen.
        (nxr/dispatch system {} [[:action/reload-words]])
        (await (settled))
        (answer! ["wort1" 100] 9 9)
        (await (settled))
        (is (= 9 (count (:words/items @store)))
            "the pull's rows replaced the ones on screen")))))


(deftest the-list-goes-back-to-its-first-row-on-the-rows-not-the-keystroke
  (async-testing "the reset rides the render that swaps the rows"
    (with-redefs [vocabulary/list-active list-stub]
      (reset! unanswered {})
      (reset! scrolls 0)
      (let [{:keys [store] :as system} (test-system)]
        (nxr/dispatch system {} [[:action/load-words {:limit 50 :search ""}]])
        (await (settled))
        (answer! ["" 50] 50 130)
        (await (settled))
        (is (zero? @scrolls) "the first page is already at its first row")

        (nxr/dispatch system {} [[:action/search-words "wort1"]])
        (is (zero? @scrolls) "not on the keystroke — these rows are 400 ms away")
        (await (debounce-elapsed))
        (answer! ["wort1" presenter/page-size] 11 11)
        (await (settled))
        (is (= 1 @scrolls) "on the rows the query brought")

        (nxr/dispatch system {} [[:action/load-words {:limit 100 :search "wort1"}]])
        (await (settled))
        (answer! ["wort1" 100] 11 11)
        (await (settled))
        (is (= 1 @scrolls) "a page of the same query does not move the reader")
        (is (= "wort1" (:words/search @store)))))))
