(ns application.example-backfill
  "The background process that fetches the examples memory is missing, one request at a time. It paces
   itself: spacing, back-off after an outage, a halt when the session is refused. Which pair to ask for,
   and which failures spare a word, are the rules of `use-cases.examples`."
  (:require
   [abort]
   [lambdaisland.glogi :as log]
   [ports.account :as account]
   [ports.examples-generator :as examples-generator]
   [ports.learner :as learner]
   [ports.page :as page]
   [ports.world :as world]
   [use-cases.examples :as examples]))


(def ^:private limits
  {:backoff-ms     5000
   :chunk-size     200
   :max-backoff-ms 300000
   :max-pause-ms   3600000
   :spacing-ms     2000
   ;; Longer than nginx's 100 s, so the proxy ends a long generation.
   :timeout-ms     110000})


(def initial-state
  "A backfill is `:status/running` or `:status/halted` (a refused session; only
   a page reload runs it again). Stopping it closes it by aborting its signal,
   not through the status."
  {:failed-subjects #{}
   :outages 0
   :status  :status/running})


(defn- backoff-ms
  [failures]
  (min (:max-backoff-ms limits) (* (:backoff-ms limits) (js/Math.pow 2 (dec failures)))))


(defn pause-ms
  "How long to wait after the `outages`th outage in a row: the back-off, or
   the `retry-after-ms` the server asked for if that is longer, at most an
   hour."
  [outages retry-after-ms]
  (min (:max-pause-ms limits) (max (backoff-ms outages) (or retry-after-ms 0))))


(defn after-response
  "`state` after `response` to the request about `subject`. A failure `examples/failures-without-example`
   names marks its subject; a server or network failure is an outage (`pause-ms` says how long it waits);
   a refused session halts the backfill. Any other answer ends the run of outages."
  [state subject {:keys [failure]} active?]
  (cond
    (nil? failure)
    (assoc state :outages 0)

    (examples/failures-without-example failure)
    (-> state
        (update :failed-subjects conj subject)
        (assoc :outages 0))

    :else
    (case failure
      :failure/unauthorized
      (assoc state :status :status/halted)

      (:failure/throttled :failure/unavailable :failure/timeout)
      (update state :outages inc)

      :failure/network
      (cond-> state
        active? (update :outages inc))

      :failure/aborted
      state)))


(defn- pause-after
  "How long to wait after a request, given the outages in a row `before` it
   and `after` it: the spacing, or the longer pause an outage asks for."
  [before after retry-after-ms]
  (max (:spacing-ms limits)
       (if (< before after)
         (pause-ms after retry-after-ms)
         0)))


(defn- unless-closed
  "`promise`, or the end of the wait when the backfill is stopped."
  [{:keys [signal]} promise]
  (js/Promise.
   (fn [resolve reject]
     (let [unlisten (abort/on-abort signal #(resolve nil))]
       (.then promise
              (fn [value]
                (unlisten)
                (resolve value))
              (fn [err]
                (unlisten)
                (reject err)))))))


(defn- ^:async missing-pair
  "The next pair to ask for among the learner's words, or nil. It reads 200 entries per task."
  [{:keys [learner page state]}]
  (loop [chunks (partition-all (:chunk-size limits) (learner/words learner))]
    (when-let [[chunk & more] (seq chunks)]
      (or (examples/next-pair learner (:failed-subjects @state) chunk)
          (do (await (page/yield page))
              (recur more))))))


(defn- ^:async ask
  "The response to the request about `subject`. A request unanswered for
   110 s counts as a timeout."
  [{:keys [controller examples-generator world]} subject]
  (let [request    (js/AbortController.)
        timed-out? (volatile! false)
        cancel     (world/clock world
                                :after
                                (:timeout-ms limits)
                                (fn []
                                  (vreset! timed-out? true)
                                  (.abort request)))]
    (reset! controller request)
    (try
      (let [response (await (examples-generator/fetch examples-generator subject (.-signal request)))]
        (cond-> response
          (and @timed-out? (= :failure/aborted (:failure response))) (assoc :failure :failure/timeout)))
      (finally
       (cancel)
       (reset! controller nil)))))


(defn- ^:async save!
  "Saves the `example` of `pair`. A save that fails marks the subject failed."
  [{:keys [learner state]} pair subject example]
  (let [saved? (try
                 (await (examples/save-example! learner pair example))
                 (catch :default err
                   (log/warn :examples/save-failed {:error (ex-message err)})
                   false))]
    (when-not saved?
      (swap! state update :failed-subjects conj subject))))


(defn- ^:async step
  "Asks for one missing example and saves it. Resolves with what the loop waits for next: `[:wait/pause ms]`,
   `[:wait/active]`, `[:wait/memory-change memory]` (nothing is missing; `memory` holds its words and
   collections), or nil when the session was refused and the backfill halts."
  [{:keys [learner page state] :as backfill}]
  (if-not (page/active? page)
    [:wait/active]
    (let [memory (learner/memory learner)]
      (if-let [pair (await (missing-pair backfill))]
        (let [subject  (examples/subject pair)
              response (await (ask backfill subject))
              before   (:outages @state)
              after    (swap! state #(after-response % subject response (page/active? page)))]
          (when (and (:failure response) (not= :failure/aborted (:failure response)))
            (log/warn :examples/fetch-failed
                      (assoc (select-keys response [:failure :message :status]) :word (:word subject))))
          (when-let [example (:example response)]
            (await (save! backfill pair subject example)))
          (when (= :status/running (:status after))
            ;; Only an outage waits longer than the spacing.
            [:wait/pause (pause-after before (:outages after) (:retry-after-ms response))]))
        [:wait/memory-change (select-keys memory [:words :collections])]))))


(defn- ^:async ready
  "Resolves once the learner is initialized: memory loaded, and the session's
   first sync pass completed or waited for long enough
   (`initialized` of the Account). Stopping the backfill ends the wait."
  [{:keys [account learner] :as backfill}]
  (await (unless-closed backfill (account/initialized account)))
  (await (learner/fresh-memory learner)))


(defn- memory-changed
  "A promise that resolves when memory's words or collections differ from `seen`, the ones a look read; at
   once when they already do. Reviews and examples don't count. When `signal` aborts first it stays pending
   and the watch is gone."
  [{:keys [learner]} seen signal]
  (let [{:keys [collections words]} seen
        differs? (fn []
                   (let [now (learner/memory learner)]
                     (not (and (identical? words (:words now))
                               (identical? collections (:collections now))))))]
    (js/Promise.
     (fn [resolve]
       (if (differs?)
         (resolve nil)
         (let [unsubscribe (volatile! (fn []))]
           (vreset! unsubscribe
                    (learner/on-change learner
                                       (fn []
                                         (when (differs?)
                                           (@unsubscribe)
                                           (resolve nil)))))
           (abort/on-abort signal #(@unsubscribe))))))))


;; Plain: an async function awaits the promise a `case` or `((:k m))` returns.
;; See docs/dev/cljs-async-pitfalls.md.
(defn- waiting
  "The promise for the `wait` a step asked for, with its `arg`."
  [{:keys [page signal world] :as backfill} wait arg]
  (case wait
    :wait/pause         (world/clock world :sleep arg signal)
    :wait/active        (page/until-active page signal)
    :wait/memory-change (memory-changed backfill arg signal)))


(defn- ^:async fetch-loop
  "Runs steps until one says stop or the backfill is closed. Each step says
   what to wait for before the next: a pause, an active page, or a change in
   memory."
  [{:keys [signal] :as backfill}]
  (await (ready backfill))
  (loop []
    (when-not (.-aborted ^js signal)
      (when-let [[wait arg] (try
                              (await (step backfill))
                              (catch :default err
                                (log/error :examples/step-failed {:error (ex-message err)})
                                [:wait/pause (pause-ms (:outages (swap! (:state backfill) update :outages inc)) nil)]))]
        (log/info :examples/waiting {:for wait})
        (await (unless-closed backfill (waiting backfill wait arg)))
        (recur)))))


(defn start!
  "Starts fetching and returns the function that stops it. A device without an
   account fetches nothing."
  [{:keys [account examples-generator learner page world]}]
  (if-not (account/exists? account)
    (do (log/info :examples/no-account {})
        (fn stop []))
    (let [controller (atom nil)
          closing    (js/AbortController.)
          backfill   {:account    account
                      :controller controller
                      :examples-generator examples-generator
                      :learner    learner
                      :page       page
                      :signal     (.-signal closing)
                      :state      (atom initial-state)
                      :world      world}]
      (page/on-inactive page #(some-> @controller .abort) (.-signal closing))
      (-> (fetch-loop backfill)
          (.catch (fn [err]
                    (log/error :examples/backfill-failed {:error (ex-message err)}))))
      (fn stop
        []
        (some-> @controller .abort)
        (.abort closing)))))
