(ns client.pages.words-actions-test
  "The word list cut from the learner's data in memory: entry, a query, the
   next page, and a change to memory while the screen is open."
  (:require
   [adapters.memory :as memory]
   [application]
   [cljs.test :refer-macros [deftest is testing]]
   [nexus.registry :as nxr]
   [pages.words.actions :as sut]
   [pages.words.effects]))


;; Minimal nexus bootstrap over `application`'s registrations, as in
;; `client.pages.home-test`.


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


(def ^:private updates
  (atom []))


(nxr/register-effect! :effect/update-word
  (fn [_ _ word]
    (swap! updates conj word)))


(defn- word-doc
  [i]
  {:_id         (str "vocab:wort" (+ 1000 i))
   :_rev        "1-a"
   :translation [{:lang "ru" :value (str "слово" i)}]
   :type        "vocab"
   :value       (str "Wort" (+ 1000 i))})


(defn- memory-of
  [n]
  (memory/with-docs memory/empty-memory (map word-doc (range n))))


(def ^:private capabilities
  {:clock       {:clock/now-ms (constantly 0)}
   :collections {:collections/active-id (constantly nil)}})


(defn- test-system
  [state]
  {:capabilities capabilities
   :store        (atom state)})


(defn- opened
  "A system with the words screen opened over `n` words."
  [n]
  (let [system (test-system {:learner/memory (memory-of n) :learner/ready? true})]
    (nxr/dispatch system {} [[:effect/enter :action/open-words]])
    system))


(deftest entry-shows-the-first-page-in-one-write
  (let [{:keys [store]} (opened 137)]
    (is (= :page/words (:page/current @store)))
    (is (= 50 (count (:words/rows @store))) "one page, not the vocabulary")
    (is (= 137 (:words/total @store)))
    (is (true? (:words/more? @store)))
    (is (= "Wort1000" (:value (first (:words/rows @store)))) "in the list's order")))


(deftest the-next-page-grows-the-rows-and-the-last-drops-the-sentinel
  (let [{:keys [store] :as system} (opened 60)]
    (nxr/dispatch system {} [[:action/show-more-words]])
    (is (= 60 (count (:words/rows @store))))
    (is (= 100 (:words/limit @store)))
    (is (false? (:words/more? @store)) "every matching row is on screen")
    (nxr/dispatch system {} [[:action/show-more-words]])
    (is (= 100 (:words/limit @store)) "nothing left to append")))


(deftest a-query-shows-its-rows-from-the-top-on-the-keystroke
  (let [{:keys [store] :as system} (opened 137)]
    (nxr/dispatch system {} [[:action/show-more-words]])
    (reset! scrolls 0)
    (nxr/dispatch system {} [[:action/search-words "wort101"]])
    (is (= "wort101" (:words/search @store)))
    (is (= 50 (:words/limit @store)) "a new query starts at the first page")
    (is (= (map #(str "Wort" %) (range 1010 1020)) (map :value (:words/rows @store))))
    (is (= 1 @scrolls) "and the list goes back to its first row at once")
    (nxr/dispatch system {} [[:action/search-words "zzz"]])
    (is (empty? (:words/rows @store)))
    (is (= 137 (:words/total @store)) "the scope is still there: no matches, not no words")))


(deftest a-change-to-memory-keeps-the-rows-loaded-and-the-open-word
  (testing "a pull, another tab or an own edit reaches the open list"
    (let [{:keys [store] :as system} (opened 137)
          change! (application/memory-changer store capabilities (constantly nil))]
      (nxr/dispatch system {} [[:action/show-more-words]])
      (nxr/dispatch system {} [[:action/open-word-edit {:id "vocab:wort1000"}]])
      (change! #(memory/with-doc % (assoc (word-doc 0) :_rev "2-b" :value "Wort1000!")))
      (is (= "Wort1000!" (:value (first (:words/rows @store)))))
      (is (= 100 (count (:words/rows @store))) "the reader is not sent back to the first page")
      (is (= {:id "vocab:wort1000"} (:words/editing @store)) "the open word stays open")
      (let [before @store]
        (change! #(memory/with-doc % (assoc (word-doc 0) :_rev "2-b" :value "Wort1000!")))
        (is (identical? before @store) "a revision memory holds renders nothing")))))


(deftest before-memory-is-ready-the-list-claims-nothing-then-fills-in
  (let [system  (test-system {:learner/memory memory/empty-memory :learner/ready? false})
        store   (:store system)
        change! (application/memory-changer store capabilities (constantly nil))]
    (nxr/dispatch system {} [[:effect/enter :action/open-words]])
    (is (= :page/words (:page/current @store)) "the screen opens at once")
    (is (nil? (:words/total @store)) "no claim about the vocabulary")
    (change! #(memory/with-docs % (map word-doc (range 3))))
    (is (nil? (:words/total @store)) "a partial load claims nothing either")
    (change! identity :ready)
    (is (= 3 (count (:words/rows @store))) "memory ready: the rows fill in without navigation")))


(deftest saving-closes-the-open-word-and-writes-it
  (let [{:keys [store] :as system} (opened 3)]
    (reset! updates [])
    (nxr/dispatch system {} [[:action/open-word-edit {:id "vocab:wort1000"}]])
    (nxr/dispatch system {} [[:action/save-word {:id "vocab:wort1000" :translation "пёс"}]])
    (is (nil? (:words/editing @store)))
    (is (= [{:id "vocab:wort1000" :translation "пёс"}] @updates))))


(deftest leaving-with-a-word-open-and-coming-back-opens-none
  (let [{:keys [store] :as system} (opened 3)]
    (nxr/dispatch system {} [[:action/open-word-edit {:id "vocab:wort1000"}]])
    (nxr/dispatch system {} [[:effect/enter :action/open-words]])
    (is (nil? (:words/editing @store)))))


(deftest content-is-pure-over-state
  (let [state {:learner/memory (memory-of 3) :learner/ready? true :words/limit 50 :words/search ""}]
    (is (= (sut/content state {:now-ms 0}) (sut/content state {:now-ms 0})))))
