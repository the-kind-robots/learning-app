(ns dictionary-sync
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]))


(def ^:private state
  (atom {:status :idle :promise nil}))


(def ^:private control-doc-id "dictionary-sync-state")


(defn loaded?
  []
  (= :ready (:status @state)))


(defn- remote-dictionary-epoch
  []
  (p/let [response (js/fetch "/db/dictionary-db/dictionary-meta"
                             #js {:cache "no-store"})]
    (if (.-ok response)
      (p/let [body (.json response)
              meta (js->clj body :keywordize-keys true)]
        ;; Wire format is snake-case JSON; tolerate kebab in case the doc
        ;; was written by a path that doesn't pass through `clj->couch`.
        (or (:dictionary-epoch meta) (:dictionary_epoch meta)))
      (throw (ex-info "Remote dictionary-meta fetch failed"
                      {:status (.-status response)})))))


(defn- stored-epoch
  [dict-db control-db]
  (p/let [control-doc (db/get control-db control-doc-id)
          ;; Fall back to the dict-db's own meta when control-db has no
          ;; record yet, so a missing control-doc doesn't reset a valid cache.
          dict-meta   (when-not (:dictionary-epoch control-doc)
                        (db/get dict-db "dictionary-meta"))]
    (or (:dictionary-epoch control-doc)
        (:dictionary-epoch dict-meta))))


(defn- save-epoch!
  [control-db epoch]
  (p/let [existing (db/get control-db control-doc-id)]
    (db/insert control-db
               (cond-> {:_id              control-doc-id
                        :type             "dictionary-sync-state"
                        :dictionary-epoch epoch}
                 (:_rev existing) (assoc :_rev (:_rev existing))))))


(defn- ensure-fresh-dict-db!
  [control-db remote-epoch]
  (let [dict-db (dbs/dictionary-db)]
    (p/let [local-epoch (stored-epoch dict-db control-db)]
      (if (and remote-epoch (not= local-epoch remote-epoch))
        (p/do
          (log/info :dictionary-sync/reset-local-cache
                    {:local-epoch  local-epoch
                     :remote-epoch remote-epoch})
          (db/destroy dict-db)
          (dbs/dictionary-db))
        dict-db))))


(defn- dictionary-loaded?
  [dict-db]
  (p/let [doc (db/get dict-db "dictionary-meta")]
    (some? doc)))


(defn- run-replication!
  [dict-db]
  (p/create
   (fn [resolve reject]
     (let [repl (db/replicate-from dict-db
                                   {:batch-size    100
                                    :batches-limit 1
                                    :checkpoint    "target"})]
       (.on
        repl
        "complete"
        (fn [info]
          (log/info :dictionary-sync/replication-complete
                    {:docs-read    (.. info -docs_read)
                     :docs-written (.. info -docs_written)})
          (resolve info)))
       (.on
        repl
        "error"
        (fn [err]
          (log/error :dictionary-sync/replication-error {:error (str err)})
          (reject err)))))))


(defn- start-sync!
  []
  (let [control-db (dbs/device-db)]
    (p/let [remote-epoch  (remote-dictionary-epoch)
            dict-db       (ensure-fresh-dict-db! control-db remote-epoch)
            loaded-before (dictionary-loaded? dict-db)]
      (p/do
        ;; Always run a one-shot pull replication on startup. PouchDB checkpoints
        ;; keep this incremental and cheap when there are no changes.
        (log/info :dictionary-sync/starting {:loaded-before loaded-before})
        (run-replication! dict-db)
        (p/let [loaded-after (dictionary-loaded? dict-db)]
          (when-not loaded-after
            (throw (ex-info "dictionary-meta doc not found after replication" {}))))
        (when remote-epoch
          (save-epoch! control-db remote-epoch))
        (log/info :dictionary-sync/complete {:loaded-before loaded-before})
        :complete))))


(defn ensure-loaded!
  []
  (let [{:keys [status promise]} @state]
    (case status
      :ready   (p/resolved true)
      :syncing promise
      ;; :idle or :failed — (re)try
      (let [p (-> (p/do
                    (start-sync!)
                    (swap! state assoc :status :ready :promise nil)
                    true)
                  (p/catch
                    (fn [err]
                      (log/error :dictionary-sync/error {:error (str err)})
                      (swap! state assoc :status :failed :promise nil)
                      false)))]
        (swap! state assoc :status :syncing :promise p)
        nil))))
