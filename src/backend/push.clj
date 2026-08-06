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


(defonce ^:private channels (atom {}))


(defn subscribe!
  [account-id channel]
  (swap! channels update account-id (fnil conj #{}) channel))


(defn unsubscribe!
  [account-id channel]
  (swap! channels
    (fn [m]
      (let [left (disj (get m account-id #{}) channel)]
        (if (empty? left) (dissoc m account-id) (assoc m account-id left))))))


(defn- send-poke!
  [channel]
  (server/send! channel "1"))


(defn- poke!
  [account-id]
  (doseq [channel (get @channels account-id)]
    (send-poke! channel)))


(defn- userdb-account-id
  [dbname]
  (some-> (re-matches #"userdb-(\d+)" dbname) second parse-long))


(defn ^:private poke-updated-accounts!
  "Turns one _db_updates answer into pokes and returns the seq to resume from."
  [{:keys [results last_seq]}]
  (doseq [id (into #{}
                   (keep #(when (= "updated" (:type %))
                            (userdb-account-id (:db_name %))))
                   results)]
    (poke! id))
  last_seq)


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
                   (poke-updated-accounts! (db/db-updates since longpoll-ms))
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
    (doto (Thread. ^Runnable fan-in-loop! "push-fan-in")
      (.setDaemon true)
      (.start))
    (t/event! ::fan-in-started {:level :info})))


(defn stop!
  []
  (reset! running? false))
