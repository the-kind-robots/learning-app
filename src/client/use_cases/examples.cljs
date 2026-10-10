(ns use-cases.examples
  "Fetches the examples memory is missing, one request at a time. The rules
   are in `specs/example-backfill/spec.md`."
  (:require
   [abort]
   [browser :as browser]
   [domain.examples :as examples]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(def ^:private limits
  {:backoff-ms         5000
   :chunk-size         200
   :first-pass-wait-ms 60000
   :max-backoff-ms     300000
   :max-pause-ms       3600000
   :spacing-ms         2000
   ;; Longer than nginx's 100 s, so the proxy ends a long generation.
   :timeout-ms         110000})


(def ^:private initial-state
  "A fetcher is `:status/running` or `:status/halted` (a refused session; only
   a page reload runs it again). `stop` closes it by aborting its signal, not
   through the status."
  {:controller      nil
   :failed-subjects #{}
   :outages         0
   :status          :status/running})


(defn- subject
  "What a request for `pair` asks the backend about."
  [{:keys [collection-name word]}]
  {:collection-name collection-name
   :translations (into []
                       (comp (filter #(= "ru" (:lang %)))
                             (map :value)
                             (filter utils/non-blank))
                       (:translation word))
   :word (:value word)})


(defn first-missing
  "The first pair of `entries` without an example whose subject has not
   failed, or nil."
  [{:keys [collections-of examples-of failed-subjects]} entries]
  (some (fn [entry]
          (->> (examples/missing-pairs entry (collections-of entry) (examples-of entry))
               (remove (comp failed-subjects subject))
               first))
        entries))


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
  "`state` after `response` to the request about `subject`. A failure of the
   pair marks its subject; a failure of the server or the network counts as
   an outage (`pause-ms` says how long it waits); a refused session stops
   the fetcher. Any answer that is not an outage ends the run of outages."
  [state subject {:keys [failure]} active?]
  (if-not failure
    (assoc state :outages 0)
    (case failure
      (:failure/invalid-subject :failure/invalid-response :failure/rejected)
      (-> state
          (update :failed-subjects conj subject)
          (assoc :outages 0))

      :failure/unauthorized
      (assoc state :status :status/halted)

      (:failure/throttled :failure/unavailable :failure/timeout)
      (update state :outages inc)

      :failure/network
      (cond-> state
        active? (update :outages inc))

      :failure/aborted
      state)))


(defn- running?
  [{:keys [signal state]}]
  (and (not (.-aborted ^js signal))
       (= :status/running (:status @state))))


(defn- unless-closed
  "`promise`, or the end of the wait when the fetcher is stopped."
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
  "The next pair to ask for in `memory`, or nil. It reads 200 entries per task."
  [{:keys [learner state]} memory]
  (let [{:learner/keys [collections-of examples-of words]} learner
        ctx {:collections-of  #(collections-of memory (:id %))
             :examples-of     #(examples-of memory [(:id %)])
             :failed-subjects (:failed-subjects @state)}]
    (loop [chunks (partition-all (:chunk-size limits) (words memory))]
      (when-let [[chunk & more] (seq chunks)]
        (if-let [pair (first-missing ctx chunk)]
          pair
          (do (await (browser/yield))
              (recur more)))))))


(defn- ^:async ask
  "The response to the request about `subject`. A request unanswered for
   110 s counts as a timeout."
  [{:keys [clock examples state]} subject]
  (let [{:clock/keys [after]} clock
        {:examples/keys [fetch]} examples
        controller (js/AbortController.)
        timed-out? (volatile! false)
        cancel     (after
                    (:timeout-ms limits)
                    (fn []
                      (vreset! timed-out? true)
                      (.abort controller)))]
    (swap! state assoc :controller controller)
    (try
      (let [response (await (fetch subject (.-signal controller)))]
        (cond-> response
          (and @timed-out? (= :failure/aborted (:failure response))) (assoc :failure :failure/timeout)))
      (finally
       (cancel)
       (swap! state assoc :controller nil)))))


(defn- ^:async save!
  "Saves the `example` of `pair`. A save that fails marks the subject failed."
  [{:keys [learner state]} pair subject example]
  (let [{:learner/keys [save-example!]} learner
        saved? (try
                 (await (save-example! {:collection-id (:collection-id pair)
                                        :example       example
                                        :word          (:value (:word pair))
                                        :word-id       (:id (:word pair))}))
                 (catch :default err
                   (log/warn :examples/save-failed {:error (ex-message err)})
                   false))]
    (when-not saved?
      (swap! state update :failed-subjects conj subject))))


(defn- ^:async step
  "Asks for one missing example and saves it. Resolves with what the loop
   waits for next: `[:wait/pause ms]` after a request, `[:wait/active]`
   when the page is not active, `[:wait/memory-change memory]` when nothing is
   missing in `memory`, which holds only its words and collections."
  [{:keys [learner page state] :as fetcher}]
  (let [{read-memory :learner/memory} learner
        {:page/keys [active?]}        page]
    (if-not (active?)
      [:wait/active]
      (let [memory (read-memory)]
        (if-let [pair (await (missing-pair fetcher memory))]
          (let [subject  (subject pair)
                response (await (ask fetcher subject))
                before   (:outages @state)
                after    (:outages (swap! state after-response subject response (active?)))]
            (when (and (:failure response) (not= :failure/aborted (:failure response)))
              (log/warn :examples/fetch-failed
                        (assoc (select-keys response [:failure :message :status]) :word (:word subject))))
            (when-let [example (:example response)]
              (await (save! fetcher pair subject example)))
            ;; Only an outage waits longer than the spacing: it is the one answer
            ;; that adds to the outages.
            [:wait/pause
             (max (:spacing-ms limits)
                  (if (< before after)
                    (pause-ms after (:retry-after-ms response))
                    0))])
          [:wait/memory-change (select-keys memory [:words :collections])])))))


;; Plain, like `waiting`: an async function awaits the promise `sleep` returns,
;; and the race would be over before it began.
(defn- first-pass-or-timeout
  "Resolves ::timed-out once `ms` pass without `first-pass`, or with nil when
   the fetcher stops; `waited` aborts the timer."
  [{:keys [clock] :as fetcher} first-pass ms ^js waited]
  (let [{:clock/keys [sleep]} clock]
    (unless-closed fetcher
                   (js/Promise.race #js [first-pass
                                         (.then (sleep ms (.-signal waited)) (constantly ::timed-out))]))))


(defn- ^:async ready
  "Resolves once memory is loaded, device-db is tidied and the session's
   first pass has completed or been waited for long enough. Stopping the
   fetcher ends the wait, and the timer is cancelled whichever ends it."
  [{:keys [learner] :as fetcher} {:capabilities/keys [sync]}]
  (let [{:learner/keys [catch-up! examples-moved loaded]} learner
        waited (js/AbortController.)]
    (try
      (await (loaded))
      (await (examples-moved))
      (when (= ::timed-out
               (await (first-pass-or-timeout fetcher (:sync/first-pass sync) (:first-pass-wait-ms limits) waited)))
        (log/info :examples/gave-up-waiting-for-sync {:waited-ms (:first-pass-wait-ms limits)}))
      (finally
       (.abort waited)))
    (await (catch-up!))))


;; Not inside `fetch-loop`: an async function awaits a promise its `case`
;; returns, and the wait would be over before `unless-closed` saw it. A port
;; called as `((:k m))` is awaited too; call it through a local name.
(defn- waiting
  "The promise for the `wait` a step asked for, with its `arg`."
  [{:keys [clock learner page signal]} wait arg]
  (let [{:learner/keys [changed-since]} learner
        {:clock/keys [sleep]}       clock
        {:page/keys [until-active]} page]
    (case wait
      :wait/pause         (sleep arg signal)
      :wait/active        (until-active signal)
      :wait/memory-change (changed-since arg signal))))


(defn- ^:async fetch-loop
  "Runs steps while the fetcher is running. Each step says what to wait for
   before the next: a pause, an active page, or a change in memory."
  [fetcher capabilities]
  (await (ready fetcher capabilities))
  (loop []
    (when (running? fetcher)
      (let [[wait arg] (try
                         (await (step fetcher))
                         (catch :default err
                           (log/error :examples/step-failed {:error (ex-message err)})
                           [:wait/pause (pause-ms (:outages (swap! (:state fetcher) update :outages inc)) nil)]))]
        (log/info :examples/waiting {:for wait})
        (await (unless-closed fetcher (waiting fetcher wait arg)))
        (recur)))))


(defn start!
  "Starts fetching and returns the function that stops it. A device without
   an account fetches nothing."
  [{:keys [learner page] :capabilities/keys [sync] :as capabilities}]
  (let [{:learner/keys [move-examples!]} learner]
    (move-examples!))
  (if-not (:sync/account-id sync)
    (do (log/info :examples/no-account {})
        (fn stop []))
    (let [{:page/keys [on-inactive]} page
          state   (atom initial-state)
          closing (js/AbortController.)
          fetcher (assoc (select-keys capabilities [:clock :examples :learner :page])
                         :signal (.-signal closing)
                         :state  state)]
      (on-inactive #(some-> (:controller @state) .abort) (.-signal closing))
      (-> (fetch-loop fetcher capabilities)
          (.catch (fn [err]
                    (log/error :examples/fetcher-failed {:error (ex-message err)}))))
      (fn stop
        []
        (some-> (:controller @state) .abort)
        (.abort closing)))))
