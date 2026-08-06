(ns push
  "Push-notified sync, ADR-0009: one longpoll loop against CouchDB's
   `_db_updates` for the whole node (the fan-in), WebSocket channels per
   account (the fan-out). A poke carries no payload — a hijacked socket
   learns only that something changed; the client pulls through its normal
   path."
  (:require
   [db :as db]
   [org.httpkit.server :as server]
   [taoensso.telemere :as t]))


(set! *warn-on-reflection* true)


(defn- userdb
  [account-id]
  (str "userdb-" account-id))


(defonce ^:private channels
  ;; userdb name -> #{channels}. Keyed by database name so a feed answer
  ;; looks its subscribers up directly; events of databases nobody can
  ;; subscribe to (dictionary-db, _global_changes) simply find no entry.
  (atom {}))


(defn subscribe!
  [account-id channel]
  (swap! channels update (userdb account-id) (fnil conj #{}) channel))


(defn unsubscribe!
  [account-id channel]
  (swap! channels
    (fn [m]
      (let [k    (userdb account-id)
            left (disj (get m k #{}) channel)]
        (if (empty? left) (dissoc m k) (assoc m k left))))))


(defn- send-poke!
  [channel]
  (server/send! channel "1"))


(defn ^:private poke-updated-dbs!
  "Turns one _db_updates answer into pokes and returns CouchDB's checkpoint
   token — the `since` for the next turn, so no update falls between turns.
   The set dedups channels, not databases: an account updated twice in one
   turn is poked once."
  [{results :results resume-from :last_seq}]
  (doseq [channel (into #{}
                        (comp (filter #(= "updated" (:type %)))
                              (mapcat #(get @channels (:db_name %))))
                        results)]
    (send-poke! channel))
  resume-from)


(defonce ^:private running? (atom false))


(def ^:private longpoll-ms
  "One longpoll turn. Long enough that an idle node costs a handful of
   requests per minute, short enough that stopping the loop is prompt."
  30000)


(defn- fan-in-loop!
  []
  (loop [since "now"]
    (when @running?
      (recur (or (try
                   (poke-updated-dbs! (db/db-updates since longpoll-ms))
                   (catch Exception e
                     (t/event! ::fan-in-failed {:level :warn :data {:error (ex-message e)}})
                     ;; CouchDB is down or _global_changes is missing: wait it
                     ;; out instead of hammering. `since` is lost on purpose —
                     ;; reconnecting clients pull anyway (ADR-0009).
                     (Thread/sleep 5000)
                     nil))
                 "now")))))


(defn start!
  "Starts the fan-in loop unless it is already running."
  []
  (when (compare-and-set! running? false true)
    ;; Daemon: the JVM may exit mid-longpoll instead of waiting out a turn.
    ;; Nothing is lost — the loop holds no state, and a poke missed at
    ;; shutdown is covered by the client's pull on reconnect.
    (doto (Thread. ^Runnable fan-in-loop! "push-fan-in")
      (.setDaemon true)
      (.start))
    (t/event! ::fan-in-started {:level :info})))


(defn stop!
  []
  (reset! running? false))
