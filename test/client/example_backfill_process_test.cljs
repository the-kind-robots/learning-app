(ns client.example-backfill-process-test
  "The example backfill, driven by a clock the test moves by hand: what it
   sends, when, and what each answer does (`specs/example-backfill/spec.md`).
   Its surroundings are fakes — memory in an atom, a fetch the test answers,
   a tab the test hides and shows — except in the last tests, which run it
   over the learner port and test databases."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [abort :as abort]
   [adapters.learner :as learner-adapter]
   [adapters.learner.documents :as documents]
   [adapters.learner.memory :as memory]
   [application.example-backfill :as backfill]
   [browser :as browser]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [are deftest is use-fixtures]]
   [domain.vocabulary :as vocabulary]
   [ports.learner :as learner-port]
   [ports.world :as world]
   [use-cases.examples :as sut]))


;;
;; A clock the test moves
;;


(defn- ^:async settled
  "Resolves once the backfill's walk has had its tasks: a walk yields to
   the event loop between its steps, on real time, while the test's clock
   stands still."
  []
  (dotimes [_ 10]
    (await (wait/settled))))


(defn- clock
  "A clock that stands still until `advance!` moves it, and calls what
   `after` scheduled as it passes."
  []
  (let [now    (atom 0)
        timers (atom (sorted-map))
        ids    (atom 0)]
    {:advance! (fn ^:async advance!
                 [ms]
                 (let [until (+ @now ms)]
                   (loop []
                     (await (settled))
                     (let [[[at id] f] (first @timers)]
                       (when (and f (<= at until))
                         (swap! timers dissoc [at id])
                         (reset! now at)
                         (f)
                         (recur))))
                   (reset! now until)
                   (await (settled))))
     :pending  #(count @timers)
     :world    (reify world/World
                 (clock [_ effect]
                   (case effect
                     :now-iso (time/now-iso)
                     :now-ms  @now))
                 (clock [_ effect ms f]
                   (case effect
                     :after (let [timer [(+ @now ms) (swap! ids inc)]]
                              (swap! timers assoc timer f)
                              #(swap! timers dissoc timer))
                     :sleep (js/Promise.
                             (fn [resolve]
                               (let [unlisten (volatile! (fn []))
                                     timer    [(+ @now ms) (swap! ids inc)]
                                     cancel   #(swap! timers dissoc timer)]
                                 (swap! timers assoc timer
                                        (fn [] (@unlisten) (resolve nil)))
                                 (vreset! unlisten (abort/on-abort f (fn [] (cancel) (@unlisten) (resolve nil)))))))))
                 (random [_] 0.5))
     :now      now}))


;;
;; Memory, as documents
;;


(defn- word-doc
  "A word created at minute `minute`, so that a higher minute is newer."
  [value minute]
  {:_id         (vocabulary/vocab-id value)
   :_rev        "1-a"
   :created-at  (str "2026-10-06T10:" (if (< minute 10) "0" "") minute ":00.000Z")
   :translation [{:lang "ru" :value (str value "-ru")}]
   :type        "vocab"
   :value       value})


(defn- collection-doc
  [id name values]
  {:_id      id
   :_rev     "1-a"
   :name     name
   :type     "collection"
   :word-ids (mapv vocabulary/vocab-id values)})


(defn- example-doc
  "The stored example of `value` in `collection-id`, as a save writes it."
  [value collection-id]
  (assoc (documents/example-doc (vocabulary/vocab-id value)
                                value
                                collection-id
                                {:structure [] :translation "перевод" :value (str value " ist da.")})
         :_rev
         "1-a"))


(defn- memory-of
  "Memory holding `docs`, its missing pairs built."
  [docs]
  (memory/with-docs memory/empty-memory docs))


;;
;; The backfill's surroundings
;;


(defn- deferred
  "A promise and the function that resolves it."
  []
  (let [resolve (atom nil)
        promise (js/Promise. #(reset! resolve %))]
    [promise @resolve]))


(defn- world
  "A backfill's surroundings over `docs`, held in memory. `:initialized` is a
   promise, resolved unless given. `:answer`, when given, is
   called with each request's subject and its place in the order of
   requests; what it returns answers the request at once, and nil leaves
   it for `answer!`. Without it, every request waits for `answer!`. The tab
   is visible until `hide!`."
  [docs {:keys [answer initialized]}]
  (let [{world :world :as ticking} (clock)
        store         (atom {:learner/memory (memory-of docs)})
        reads         (atom 0)
        requests      (atom [])
        saves         (atom [])
        online        (atom true)
        visible       (atom true)
        on-inactive   (atom nil)
        waiting       (atom [])
        active?       #(and @visible @online)
        change-page!  (fn []
                        (if (active?)
                          (let [resolvers @waiting]
                            (reset! waiting [])
                            (run! #(% nil) resolvers))
                          (some-> @on-inactive (apply []))))]
    (merge
     ticking
     {:capabilities
      {:world    world
       :examples-generator {:examples-generator/fetch     (fn [subject ^js signal]
                                        (js/Promise.
                                         (fn [resolve reject]
                                           (.addEventListener signal "abort" #(resolve {:failure :failure/aborted}))
                                           (let [n (count @requests)]
                                             (swap! requests
                                               conj
                                               {:at      (world/clock world :now-ms)
                                                :subject subject
                                                :reject  reject
                                                :resolve resolve
                                                :signal  signal})
                                             (when-let [answered (some-> answer (apply [subject n]))]
                                               (resolve answered))))))}
       :browser  {:browser/active?      active?
                  :browser/yield        browser/yield
                  :browser/until-active (fn []
                                       (js/Promise.
                                        (fn [resolve]
                                          (if (active?)
                                            (resolve nil)
                                            (swap! waiting conj resolve)))))
                  :browser/on-inactive  (fn [f ^js signal]
                                       (reset! on-inactive f)
                                       (.addEventListener signal "abort" #(reset! on-inactive nil)))}
       :learner  (assoc {}
                        :learner/account        {:account/exists? true}
                        :learner/fresh-memory   #(js/Promise.resolve (:learner/memory @store))
                        :learner/initialized    (or initialized (js/Promise.resolve nil))
                        :learner/memory         (fn []
                                                  (:learner/memory @store))
                        :learner/on-change      #(learner-adapter/on-change {:store store} %)
                        :learner/insert!         (fn [_ example]
                                                  (swap! saves conj [(:word example)])
                                                  (swap! store
                                                    update
                                                    :learner/memory
                                                    memory/with-docs
                                                    [(assoc (documents/example-doc (:word-id example)
                                                                                   (:word example)
                                                                                   (:collection-id example)
                                                                                   (:example example))
                                                            :_rev
                                                            "1-a")])
                                                  (js/Promise.resolve true)))}
      :change! #(swap! store update :learner/memory memory/with-docs %)
      :hide! (fn [] (reset! visible false) (change-page!))
      :reads reads
      :store store
      :go-offline! (fn [] (reset! online false) (change-page!))
      :go-online! (fn [] (reset! online true) (change-page!))
      :online online
      :on-inactive on-inactive
      :requests requests
      :saves saves
      :show! (fn [] (reset! visible true) (change-page!))
      :visible visible})))


(defn- asked
  "The words the requests so far asked about, in order."
  [{:keys [requests]}]
  (mapv (comp :word :subject) @requests))


(defn- answer!
  "Answers the `n`-th request with `answer`."
  [{:keys [requests]} n answer]
  ((:resolve (nth @requests n)) answer))


(defn- reject!
  "Rejects the `n`-th request's promise, which the real port promises
   never to do."
  [{:keys [requests]} n]
  ((:reject (nth @requests n)) (js/Error. "boom")))


(defn- start!
  "Starts the backfill as the app does."
  [capabilities]
  (backfill/start! capabilities))


(def ^:private an-example
  {:example {:structure [] :translation "перевод" :value "Ein Satz."}})


(defn- started
  "Starts the backfill in `world`; hands back the world with `:stop`."
  [world]
  (assoc world :stop (start! (:capabilities world))))


(defn- ^:async run
  "Starts the backfill over `docs` with `options`, calls `f` with the world,
   and stops the backfill afterwards."
  [docs options f]
  (let [world (world docs options)]
    ;; A look at the words is what `reads` counts.
    (with-redefs [learner-port/words (fn [memory]
                                       (swap! (:reads world) inc)
                                       (memory/words memory))]
      (let [world (started world)]
        (try
          (await (f world))
          (finally
           ((:stop world))))))))


(def ^:private three-words
  [(word-doc "Hund" 1) (word-doc "Katze" 3) (word-doc "Maus" 2)])


;;
;; The pure parts
;;


(deftest the-next-pair-is-the-first-entry-without-an-example
  (let [hund   {:id "vocab:hund" :value "Hund" :translation []}
        katze  {:id "vocab:katze" :value "Katze" :translation []}
        tiere  (collection-doc "coll-tiere" "Tiere" ["Hund"])
        held   (example-doc "Hund" nil)
        asking (fn [{:keys [docs failed-subjects]} entries]
                 (sut/next-pair (memory-of (or docs []))
                                (or failed-subjects #{})
                                entries))]
    (is (= {:word hund} (asking {} [hund katze])))
    (is (= {:word katze} (asking {:docs [held]} [hund katze]))
        "an entry with an example is passed over")
    (is (= {:collection-id "coll-tiere" :collection-name "Tiere" :word hund}
           (asking {:docs [tiere]} [hund katze]))
        "an entry in a collection is asked for there")
    (is (= {:word katze}
           (asking {:failed-subjects #{{:collection-name nil :translations [] :word "Hund"}}} [hund katze]))
        "an entry whose subject failed is passed over")
    (is (nil? (asking {} [])))))


(deftest a-request-asks-about-the-russian-translations-and-the-part-of-speech
  (is (= {:collection-name nil :pos "noun" :translations ["пёс"] :word "Hund"}
         (sut/subject {:word {:pos         "noun"
                              :translation [{:lang "ru" :value "пёс"} {:lang "en" :value "dog"}]
                              :value       "Hund"}}))))


(defn- answered
  [state subject response active?]
  (backfill/after-response state subject response active?))


(deftest outages-pause-by-a-doubling-back-off-and-never-stop
  (let [state-after (fn [failures response]
                      (reduce (fn [state _] (answered state {} response true))
                              backfill/initial-state
                              (range failures)))
        pause-after (fn [failures response]
                      (backfill/pause-ms (:outages (state-after failures response)) (:retry-after-ms response)))]
    (are [failures pause] (= pause (pause-after failures {:failure :failure/unavailable}))
     1 5000
     2 10000
     3 20000
     6 160000
     9 300000)
    (is (= :status/running (:status (state-after 50 {:failure :failure/unavailable})))
        "however many outages, the tab keeps asking")
    (is (= 300000 (pause-after 50 {:failure :failure/unavailable})) "at most 5 min")
    (is (= 600000 (pause-after 1 {:failure :failure/throttled :retry-after-ms 600000}))
        "Retry-After, when longer")
    (is (= 3600000 (pause-after 1 {:failure :failure/throttled :retry-after-ms 86400000}))
        "at most an hour")
    (is (zero? (:outages (state-after 2 {:example {}}))) "an example ends the run")))


(deftest each-failure-kind-has-one-effect
  (let [after (fn [response active?]
                (answered backfill/initial-state :subject response active?))]
    (are [kind] (= #{:subject} (:failed-subjects (after {:failure kind} true)))
     :failure/invalid-subject
     :failure/invalid-response
     :failure/rejected)
    (is (= :status/halted (:status (after {:failure :failure/unauthorized} true))))
    (is (= 1 (:outages (after {:failure :failure/network} true))))
    (is (= 0 (:outages (after {:failure :failure/network} false))) "when it may not send, a network failure is nothing")
    (is (= 0 (:outages (after {:failure :failure/aborted} true))))))


(deftest an-answer-that-is-not-an-outage-ends-the-run-of-outages
  (let [outage (fn [state] (answered state :subject {:failure :failure/unavailable} true))
        reject (fn [state] (answered state :subject {:failure :failure/rejected} true))
        state  (nth (iterate (comp reject outage) backfill/initial-state) 20)]
    (is (= 5000 (backfill/pause-ms (:outages (outage state)) nil)) "outage, 422, outage, 422: the back-off never grows")
    (is (= 0 (:outages state)))
    (are [kind] (= 0 (:outages (answered (assoc backfill/initial-state :outages 3) :subject {:failure kind} true)))
     :failure/invalid-subject
     :failure/invalid-response
     :failure/rejected)))


;;
;; When it starts
;;


(deftest nothing-is-sent-before-the-learner-is-initialized
  (async-testing "memory loaded and the first pass done, whatever the learner says it waits for; the session cookie is written before"
    (let [[initialized initialized!] (deferred)]
      (await
       (run three-words
            {:initialized initialized}
            (^:async fn
             [{:keys [advance!] :as world}]
             (await (advance! 30000))
             (is (empty? (asked world)) "nothing before the learner is initialized")
             (initialized! nil)
             (await (advance! 0))
             (is (= ["Hund"] (asked world)) "initialized, and the first entry goes")))))))


(deftest memory-catches-up-before-the-first-pick
  (async-testing "an example the pass brought but the feed dropped is in memory before anything is asked"
    (let [w        (world three-words {})
          catch-up (fn []
                     (swap! (:store w) update :learner/memory memory/with-docs [(example-doc "Hund" nil)])
                     (js/Promise.resolve nil))
          stop     (start! (assoc-in (:capabilities w) [:learner :learner/fresh-memory] catch-up))]
      (try
        (await ((:advance! w) 0))
        (is (= ["Katze"] (asked w)) "Hund was answered by the catch-up")
        (finally
         (stop))))))


;;
;; What it asks for
;;


(deftest one-request-at-a-time-two-seconds-apart
  (async-testing "three pairs, slow answers: the next request waits for the answer and then for the spacing"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! requests] :as world}]
           (await (advance! 30000))
           (is (= ["Hund"] (asked world)) "a second request does not start while the first waits")
           (answer! world 0 an-example)
           (await (advance! 1999))
           (is (= 1 (count @requests)) "and not sooner than 2 s after the answer")
           (await (advance! 1))
           (is (= ["Hund" "Katze"] (asked world)))
           (is (= [0 32000] (mapv :at @requests))))))))


(deftest an-answer-is-saved-at-once
  (async-testing "one example, one write, before the next request"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! saves] :as world}]
           (await (advance! 0))
           (answer! world 0 an-example)
           (await (advance! 0))
           (is (= [["Hund"]] @saves)))))))


(deftest an-answered-pair-is-not-asked-for-again
  (async-testing "every pair is answered once; then nothing is asked"
    (await
     (run three-words
          {:answer (constantly an-example)}
          (^:async fn
           [{:keys [advance!] :as world}]
           (await (advance! 2000))
           (await (advance! 2000))
           (is (= ["Hund" "Katze" "Maus"] (asked world)))
           (await (advance! 120000))
           (is (= 3 (count (asked world)))))))))


(deftest an-example-that-arrives-first-answers-its-pair
  (async-testing "memory takes the example before the request for it is sent"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! change!] :as world}]
           (await (advance! 0))
           (is (= ["Hund"] (asked world)))
           ;; Katze is next, and its example arrives by replication first.
           (change! [(example-doc "Katze" nil)])
           (answer! world 0 an-example)
           (await (advance! 2000))
           (is (= ["Hund" "Maus"] (asked world))))))))


(deftest a-deleted-theme-asks-for-nothing
  (async-testing "a theme deleted before its pair is asked for"
    (await
     (run [(word-doc "Katze" 2)
           (word-doc "Maus" 1)
           (collection-doc "coll-tiere" "Tiere" ["Maus"])
           (example-doc "Maus" nil)]
          {}
          (^:async fn
           [{:keys [advance! change!] :as world}]
           (await (advance! 0))
           (is (= ["Katze"] (asked world)))
           (change! [{:_deleted true :_id "coll-tiere" :_rev "2-x"}])
           (answer! world 0 an-example)
           (await (advance! 60000))
           (is (= ["Katze"] (asked world)) "Maus is in no theme now, and its example answers the main card"))))))


(deftest a-word-added-or-replicated-ends-the-wait-for-memory
  (async-testing "nothing to ask: it sleeps until memory takes a word or a collection, with no re-check"
    (await
     (run []
          {}
          (^:async fn
           [{:keys [advance! change!] :as world}]
           (await (advance! 3600000))
           (is (empty? (asked world)) "no periodic look")
           (change! [(word-doc "Igel" 5)])
           (await (advance! 0))
           (is (= ["Igel"] (asked world)) "a word taken into memory wakes it at once")
           (answer! world 0 an-example)
           (await (advance! 2000))
           (change! [(collection-doc "coll-tiere" "Tiere" ["Igel"])])
           (await (advance! 0))
           (is (= ["Igel" "Igel"] (asked world)) "a collection wakes it too"))))))


(deftest a-change-during-a-look-that-found-nothing-causes-one-more-look
  (async-testing "the look read memory before the words came, so it looks again, once"
    (await
     (run []
          {}
          (^:async fn
           [{:keys [advance! change! reads] :as world}]
           (await (wait/until #(= 1 @reads)))
           (change! [(word-doc "Igel" 5)])
           (change! [(word-doc "Maus" 6)])
           (await (advance! 0))
           (is (= 2 @reads) "one more look, not one per change")
           (is (= ["Igel"] (asked world)) "and the second look saw the words"))))))


(deftest an-answer-does-not-end-the-wait-for-memory
  (async-testing "an example or a review changes cards, not words or collections"
    (await
     (run
      [(word-doc "Hund" 1)]
      {:answer (constantly {:failure :failure/rejected :status 422})}
      (^:async fn
       [{:keys [advance! change! reads]}]
       (await (advance! 2000))
       (let [before @reads]
         (change!
          [(example-doc "Hund" nil)
           {:_id "review-1" :_rev "1-a" :type "review" :word-id "vocab:hund" :created-at "2026-10-06T10:00:00.000Z"}])
         (await (advance! 0))
         (is (= before @reads) "memory was not read again"))
       (let [before @reads]
         (change! [(word-doc "Katze" 3)])
         (await (advance! 0))
         (is (< before @reads) "a word did")))))))


(deftest a-memory-change-does-not-cut-a-pause-short
  (async-testing "a word added during a back-off waits for the back-off"
    (await
     (run [(word-doc "Hund" 1)]
          {}
          (^:async fn
           [{:keys [advance! change!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/unavailable :status 503})
           (change! [(word-doc "Igel" 5)])
           (await (advance! 4999))
           (is (= ["Hund"] (asked world)))
           (await (advance! 1))
           (is (= 2 (count (asked world)))))))))


(deftest a-themed-pair-asks-with-the-theme-s-name
  (async-testing "the subject carries the glosses and the collection's name"
    (await
     (run [(word-doc "Hund" 1) (collection-doc "coll-tiere" "Tiere" ["Hund"]) (example-doc "Hund" nil)]
          {}
          (^:async fn
           [{:keys [advance! requests]}]
           (await (advance! 0))
           (is (= [{:collection-name "Tiere" :translations ["Hund-ru"] :word "Hund"}]
                  (mapv :subject @requests))
               "the main card's example does not answer a theme"))))))


(deftest a-vocabulary-of-several-chunks-is-read-to-its-end
  (async-testing "the only entry without an example is the last of 450"
    (await
     (run (-> (mapv #(word-doc (str "Wort" (+ 100 %)) 0) (range 449))
              (into (map #(example-doc (str "Wort" (+ 100 %)) nil) (range 449)))
              (conj (word-doc "Zebra" 0)))
          {}
          (^:async fn
           [world]
           (await (wait/until #(seq (asked world))))
           (is (= ["Zebra"] (asked world))))))))


;;
;; How fast
;;


(deftest a-request-that-hangs-is-aborted-at-110-seconds
  (async-testing "and counts as an unavailable generator: a pause, and the same pair after it"
    (await
     (run [(word-doc "Hund" 1) (word-doc "Katze" 2)]
          {}
          (^:async fn
           [{:keys [advance! requests] :as world}]
           (await (advance! 109999))
           (is (= ["Hund"] (asked world)))
           (is (not (.-aborted (:signal (first @requests)))))
           (await (advance! 1))
           (is (.-aborted (:signal (first @requests))))
           (await (advance! 4999))
           (is (= 1 (count @requests)) "paused for 5 s")
           (await (advance! 1))
           (is (= ["Hund" "Hund"] (asked world)) "Hund timed out and is asked again"))))))


(deftest stopping-ends-a-pause-at-once
  (async-testing "a stop during the spacing after a request leaves no timer"
    (await
     (run [(word-doc "Hund" 1)]
          {:answer (constantly an-example)}
          (^:async fn
           [{:keys [advance! pending stop]}]
           (await (advance! 0))
           (is (pos? (pending)) "the backfill is pausing")
           (stop)
           (await (advance! 0))
           (is (zero? (pending)) "the pause's timer is gone"))))))


(defn- ^:async with-abort-listeners
  "Calls `f` with a function that counts the \"abort\" listeners now on
   signals other than `except`, the signals of requests. The count is
   what was added less what was removed or fired."
  [f]
  (let [proto   (.-prototype js/AbortSignal)
        adds    (.-addEventListener proto)
        removes (.-removeEventListener proto)
        held    (js/Map.)]
    ;; Plain JavaScript: the wrappers need the caller's `this` and any rest.
    (set! (.-addEventListener proto)
          ((js/Function. "orig" "held"
                         "return function (type, listener, ...rest) {
                            if (type === 'abort') held.set(listener, this);
                            return orig.call(this, type, listener, ...rest);
                          }")
           adds held))
    (set! (.-removeEventListener proto)
          ((js/Function. "orig" "held"
                         "return function (type, listener, ...rest) {
                            if (type === 'abort') held.delete(listener);
                            return orig.call(this, type, listener, ...rest);
                          }")
           removes held))
    (try
      (await (f (fn [except]
                  (count (remove (fn [signal]
                                   (some #(identical? signal %) except))
                                 (array-seq (js/Array.from (.values held))))))))
      (finally
       (set! (.-addEventListener proto) adds)
       (set! (.-removeEventListener proto) removes)))))


(deftest pauses-leave-no-listener-behind
  (async-testing "after many pauses the backfill's signal holds the current wait's listeners only"
    (let [docs (mapv #(word-doc (str "w" %) %) (range 8))]
      (await
       (with-abort-listeners
        (^:async fn
         [held]
         (await
          (run docs
               {:answer (constantly an-example)}
               (^:async fn
                [{:keys [advance! requests]}]
                (dotimes [_ 6]
                  (await (advance! 5000)))
                (is (<= 6 (count @requests)) "the backfill paused at least six times")
                (is (<= (held (map :signal @requests)) 3)
                    "only the wait it is in now listens (its pause and its race), plus the one release of the inactivity watch"))))))))))


(deftest stopping-leaves-no-watch-on-the-store
  (async-testing "a backfill waiting for a change in memory holds a watch, and stop removes it"
    (await
     (run [(word-doc "Hund" 1)]
          {:answer (constantly an-example)}
          (^:async fn
           [{:keys [advance! on-inactive stop store]}]
           (await (advance! 5000))
           (await (advance! 5000))
           (is (= 1 (count (.-watches ^js store))) "it waits for memory to change")
           (is (some? @on-inactive) "it listens for the page going inactive")
           (stop)
           (is (zero? (count (.-watches ^js store))) "stop removed the watch")
           (is (nil? @on-inactive) "stop removed the going-inactive listener"))))))


(deftest a-hidden-tab-sends-nothing
  (async-testing "a tab hidden from the start asks once it is shown"
    (let [w (world three-words {})]
      (reset! (:visible w) false)
      (let [{:keys [advance! show! stop] :as world} (started w)]
        (try
          (await (advance! 60000))
          (is (empty? (asked world)))
          (show!)
          (await (advance! 0))
          (is (= ["Hund"] (asked world)))
          (finally
           (stop)))))))


(deftest a-device-without-an-account-asks-nothing
  (async-testing "the endpoint would answer 401, so nothing is sent"
    (let [w    (world three-words {})
          stop (start! (assoc-in (:capabilities w) [:learner :learner/account :account/exists?] false))]
      (try
        (await ((:advance! w) 600000))
        (is (empty? (asked w)))
        (finally
         (stop))))))


(deftest a-retry-after-is-capped-at-an-hour
  (async-testing "a day asked for, an hour waited"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! requests] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/throttled :retry-after-ms 86400000 :status 429})
           (await (advance! 3599999))
           (is (= 1 (count @requests)))
           (await (advance! 1))
           (is (= 2 (count @requests))))))))


(deftest many-outages-keep-pausing-and-never-halt
  (async-testing "the pause stops growing at 5 min, and the tab keeps asking, hidden and shown or not"
    (await
     (run three-words
          {:answer (constantly {:failure :failure/throttled :status 429})}
          (^:async fn
           [{:keys [advance! hide! show! requests]}]
           (await (advance! 3600000))
           (let [ats (mapv :at @requests)]
             (is (= [0 5000 15000 35000 75000 155000 315000 615000] (take 8 ats))
                 "each pause doubles up to 5 min")
             (is (< 10 (count ats)) "the tab asks on after the sixth outage")
             (is (every? #(= 300000 %) (map - (rest (drop 8 ats)) (drop 8 ats)))))
           (await (hide!))
           (show!)
           (let [before (count @requests)]
             (await (advance! 3600000))
             (is (< before (count @requests)))))))))


(deftest showing-the-tab-does-not-end-a-run-of-failures
  (async-testing "three failures, the tab hidden and shown: the back-off goes on from three, not from zero"
    (await
     (run three-words
          {:answer (constantly {:failure :failure/throttled :status 429})}
          (^:async fn
           [{:keys [advance! hide! show! requests]}]
           (await (advance! 15000))
           (is (= 3 (count @requests)))
           (await (hide!))
           (show!)
           (await (advance! 3600000))
           (is (= [0 5000 15000 35000 75000] (take 5 (map :at @requests)))
               "the fourth pause is 20 s, not 5"))))))


(deftest an-unencodable-subject-skips-only-its-pair
  (async-testing "half an emoji in a theme name cannot go into a URL; the next pair is asked for"
    (await
     (run [(word-doc "Hund" 1) (word-doc "Katze" 2)]
          {}
          (^:async fn
           [{:keys [advance! requests] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/invalid-subject})
           (await (advance! 2000))
           (is (= ["Hund" "Katze"] (asked world)))
           (is (= 2000 (:at (second @requests))) "no pause"))))))


(deftest offline-sends-nothing-until-online
  (async-testing "the online event wakes it"
    (let [w (world three-words {})]
      (reset! (:online w) false)
      (let [{:keys [advance! go-online! stop] :as world} (started w)]
        (try
          (await (advance! 60000))
          (is (empty? (asked world)))
          (go-online!)
          (await (advance! 0))
          (is (= ["Hund"] (asked world)))
          (finally
           (stop)))))))


;;
;; What each answer does
;;


(deftest a-throttle-pauses-for-retry-after-and-resumes-on-its-own
  (async-testing "429 with Retry-After: 30; the throttled pair is asked again"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/throttled :retry-after-ms 30000 :status 429})
           (await (advance! 29999))
           (is (= ["Hund"] (asked world)) "nothing inside Retry-After")
           (await (advance! 1))
           (is (= ["Hund" "Hund"] (asked world)) "it resumes on its own, with the same pair"))))))


(deftest an-unavailable-generator-pauses-and-marks-nothing
  (async-testing "503 with Retry-After: 30: the server's problem, not the pair's; the same pair after the pause"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/unavailable :retry-after-ms 30000 :status 503})
           (await (advance! 29999))
           (is (= ["Hund"] (asked world)))
           (await (advance! 1))
           (is (= ["Hund" "Hund"] (asked world)) "the pair was not marked"))))))


(deftest a-rejected-question-skips-only-its-pair
  (async-testing "422: no pause beyond the spacing"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! requests] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/rejected :status 422})
           (await (advance! 2000))
           (is (= ["Hund" "Katze"] (asked world)))
           (is (= 2000 (:at (second @requests))))
           (answer! world 1 an-example)
           (await (advance! 2000))
           (answer! world 2 an-example)
           (await (advance! 600000))
           (is (= ["Hund" "Katze" "Maus"] (asked world)) "Hund never again"))))))


(deftest a-pair-that-failed-is-asked-again-once-its-entry-is-edited
  (async-testing "the failure belongs to the subject, and the subject changed"
    (await
     (run [(word-doc "Hund" 1)]
          {}
          (^:async fn
           [{:keys [advance! change!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/invalid-response :status 200})
           (await (advance! 60000))
           (is (= ["Hund"] (asked world)))
           (change!
            [(assoc (word-doc "Hund" 1)
                    :_rev        "2-b"
                    :translation [{:lang "ru" :value "пёс"}])])
           (await (advance! 0))
           (is (= ["Hund" "Hund"] (asked world))))))))


(deftest an-authentication-refusal-stops-the-tab
  (async-testing "401: nothing more, not even when the tab is hidden and shown again"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance! hide! show!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/unauthorized :status 401})
           (await (advance! 600000))
           (is (= ["Hund"] (asked world)))
           (await (hide!))
           (await (advance! 0))
           (show!)
           (await (advance! 600000))
           (is (= ["Hund"] (asked world)) "halted until the page is reloaded"))))))


(deftest a-network-failure-pauses-only-while-online
  (async-testing "online, it is shared and the pair stays eligible; offline, it is nothing"
    (await
     (run [(word-doc "Hund" 1)]
          {}
          (^:async fn
           [{:keys [advance! go-offline! go-online!] :as world}]
           (await (advance! 0))
           (answer! world 0 {:failure :failure/network :message "Failed to fetch"})
           (await (advance! 4999))
           (is (= ["Hund"] (asked world)))
           (await (advance! 1))
           (is (= ["Hund" "Hund"] (asked world)) "after the back-off, the same pair")
           (go-offline!)
           (answer! world 1 {:failure :failure/network :message "Failed to fetch"})
           (await (advance! 600000))
           (is (= 2 (count (asked world))) "offline: nothing until online")
           (go-online!)
           (await (advance! 0))
           (is (= 3 (count (asked world))) "and no pause was added for it"))))))


(deftest hiding-the-tab-aborts-and-marks-nothing
  (async-testing "the tab is hidden mid-request; shown again, it asks for the same pair"
    (await
     (run [(word-doc "Hund" 1)]
          {}
          (^:async fn
           [{:keys [advance! requests hide! show!] :as world}]
           (await (advance! 0))
           (await (hide!))
           (is (.-aborted (:signal (first @requests))))
           (await (advance! 600000))
           (is (= ["Hund"] (asked world)) "hidden, no request")
           (show!)
           (await (advance! 0))
           (is (= ["Hund" "Hund"] (asked world))))))))


(deftest going-offline-aborts-the-request-in-flight
  (async-testing "nothing is marked; back online, it asks for the same pair"
    (await
     (run [(word-doc "Hund" 1)]
          {}
          (^:async fn
           [{:keys [advance! go-offline! go-online! requests] :as world}]
           (await (advance! 0))
           (go-offline!)
           (is (.-aborted (:signal (first @requests))))
           (await (advance! 600000))
           (is (= ["Hund"] (asked world)) "offline, no request")
           (go-online!)
           (await (advance! 0))
           (is (= ["Hund" "Hund"] (asked world))))))))


(deftest a-throwing-step-is-retried-after-the-back-off
  (async-testing
    "the port promises never to reject; if it does, that is an outage and nobody has to wake the backfill"
    (await
     (run three-words
          {}
          (^:async fn
           [{:keys [advance!] :as world}]
           (await (advance! 0))
           (reject! world 0)
           (await (advance! 4999))
           (is (= ["Hund"] (asked world)))
           (await (advance! 1))
           (is (= ["Hund" "Hund"] (asked world))))))))


;;
;; Over the learner port and the databases
;;


(def user-db-name (db-fixtures/db-name "client.example-backfill-process-test.user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name]))


(deftest a-start-asks-for-what-memory-lacks-and-saves-it
  (async-testing "an example user-db holds answers its pair; the fetched one reaches user-db"
    (await
     (db-fixtures/with-test-dbs
      [user-db-name]
      (^:async fn
       [[user-db]]
       (let [dbs {:user/db user-db}]
         (await (db-seed/seed-vocabulary! user-db
                                          [{:value "Hund" :translation "собака"}
                                           {:value "Katze" :translation "кошка"}]))
         (await (db-seed/seed-examples! user-db
                                        [{:_id         "3F2B9C1A"
                                          :word-id     (vocabulary/vocab-id "Hund")
                                          :word        "Hund"
                                          :value       "Der Hund bellt."
                                          :translation "Собака лает."}]))
         (await
          (learner/with-learner
           dbs
           time/world
           (^:async fn
            [{:keys [store] :as started}]
            (let [{:keys [advance!] :as w} (world []
                                                  {:answer (constantly {:example {:structure   []
                                                                                  :translation "Кошка спит."
                                                                                  :value       "Die Katze schläft."}})})
                  stop (start! (-> (:capabilities w)
                                       (assoc :learner (assoc (:learner started)
                                                              :learner/account {:account/exists? true}
                                                              :learner/initialized (js/Promise.resolve nil)))))]
              (try
                ;; Memory changed as it started; the backfill picks once it
                ;; has been still, by the test's clock.
                (await (wait/until (fn [] (.then (advance! 1000) #(seq (asked w))))))
                (is (= ["Katze"] (asked w)) "the stored example answers Hund")
                (await (advance! 2000))
                (await (wait/until #(seq (memory/examples-of (:learner/memory @store)
                                                             [(vocabulary/vocab-id "Katze")]))))
                (is (= ["Die Katze schläft."]
                       (map :value (memory/examples-of (:learner/memory @store) [(vocabulary/vocab-id "Katze")]))))
                (is (= #{"Der Hund bellt." "Die Katze schläft."}
                       (set (map :value (await (db-queries/fetch-examples user-db))))))
                (finally
                 (stop)))))))))))))
