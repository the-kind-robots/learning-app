(ns client.pages.words-actions-test
  "The word list cut from the learner's data in memory: entry, a query, the
   next page, and a change to memory while the screen is open."
  (:require
   [adapters.learner.memory :as memory]
   [application]
   [cljs.test :refer-macros [deftest is testing]]
   [nexus.registry :as nxr]
   [pages.words.actions :as sut]
   [ports.learner :as ports]
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


(def ^:private painted
  "What waits for the next paint; the tests say when it comes."
  (atom []))


(nxr/register-effect! :effect/after-paint
  (fn [_ _ actions]
    (swap! painted conj actions)))


(defn- paint!
  [system]
  (let [[waiting _] (reset-vals! painted [])]
    (doseq [actions waiting]
      (nxr/dispatch system {} actions))))


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
  {:clock   {:clock/now-ms (constantly 0)}
   :learner (assoc ports/reads :learner/active-collection (constantly nil))})


(defn- test-system
  [state]
  {:capabilities capabilities
   :store        (atom state)})


(defn- entered
  "A system with the words screen just opened over `n` words, not yet
   painted."
  [n]
  (let [system (test-system {:learner/memory (memory-of n) :learner/loaded? true})]
    (reset! painted [])
    (nxr/dispatch system {} [[:effect/enter :action/open-words]])
    system))


(defn- opened
  "A system with the words screen opened over `n` words and painted."
  [n]
  (doto (entered n) paint!))


(deftest entry-shows-the-first-rows-then-the-page
  (let [{:keys [store] :as system} (entered 137)]
    (is (= :page/words (:page/current @store)))
    (is (= 20 (count (:words/rows @store))) "what renders within the frame of the tap")
    (paint! system)
    (is (= 50 (count (:words/rows @store))) "one page after the paint, not the vocabulary")
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
    (paint! system)
    (is (= "wort101" (:words/search @store)))
    (is (= 50 (:words/limit @store)) "a new query starts at the first page")
    (is (= (map #(str "Wort" %) (range 1010 1020)) (map :value (:words/rows @store))))
    (is (= 1 @scrolls) "and the list goes back to its first row at once")
    (nxr/dispatch system {} [[:action/search-words "zzz"]])
    (is (empty? (:words/rows @store)))
    (is (= 137 (:words/total @store)) "the scope is still there: no matches, not no words")))


(deftest a-change-to-memory-leaves-the-open-list-until-it-is-computed-again
  (testing "a pull or another tab changes memory, not the list on screen"
    (let [{:keys [store] :as system} (opened 137)]
      (nxr/dispatch system {} [[:action/show-more-words]])
      (nxr/dispatch system {} [[:action/open-word-edit {:id "vocab:wort1000"}]])
      (nxr/dispatch system
                    {}
                    [[:effect/memory-changed :user/db [(assoc (word-doc 0) :_rev "2-b" :value "Wort1000!")] 2]])
      (is (= "Wort1000" (:value (first (:words/rows @store)))) "the open list stays as it was")
      (testing "the reader's own edit computes it again, rows and open word kept"
        (nxr/dispatch system {} [[:effect/enter :action/refresh-page]])
        (is (= "Wort1000!" (:value (first (:words/rows @store)))))
        (is (= 100 (count (:words/rows @store))) "the reader is not sent back to the first page")
        (is (= {:id "vocab:wort1000"} (:words/editing @store)) "the open word stays open")))))


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
  (let [state {:learner/memory (memory-of 3) :learner/loaded? true :words/limit 50 :words/search ""}]
    (is (= (sut/content state {:learner ports/reads :now-ms 0}) (sut/content state {:learner ports/reads :now-ms 0})))))
