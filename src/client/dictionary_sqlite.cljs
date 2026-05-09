(ns dictionary-sqlite
  (:require ["@sqlite.org/sqlite-wasm" :default sqlite3-init-module]
            [lambdaisland.glogi :as log]
            [promesa.core :as p]
            [utils :as utils]))


(defonce ^:private db-state
  ;; nil = uninitialized | :loading = in-progress | {:db db :hash hash12} = ready
  (atom nil))


(def ^:private suggest-sql
  "SELECT
     l.id    AS lemma_id,
     l.value AS lemma,
     l.pos,
     l.rank,
     t.value AS translation,
     MAX(CASE WHEN sf.normalized_form = ? THEN 1 ELSE 0 END) AS has_exact
   FROM surface_forms sf
   JOIN lemmas l ON l.id = sf.lemma_id
   LEFT JOIN translations t ON t.lemma_id = l.id AND t.rank = 1
   WHERE sf.normalized_form >= ? AND sf.normalized_form < ?
   GROUP BY l.id
   ORDER BY has_exact DESC, l.rank ASC
   LIMIT 10")


(defn- pool-filename [hash12]
  ;; Leading "/" ensures importDb and xOpen agree on the key in #mapFilenameToSAH.
  ;; xOpen normalises via new URL(name, "file://localhost/").pathname which always
  ;; produces a leading-slash form; importDb stores raw — so we pre-normalise here.
  (str "/dict." hash12 ".sqlite"))


(defn- fetch-manifest! []
  (p/let [resp (js/fetch "/dictionary/manifest")
          json (.json resp)]
    (js->clj json :keywordize-keys true)))


(defn init!
  "Fire-and-forget: initialises the opfs-sahpool VFS and opens the current
   dictionary SQLite file. Errors are logged, never rethrown."
  []
  (when (nil? @db-state)
    (reset! db-state :loading)
    (-> (p/let [manifest (fetch-manifest!)
                hash     (:hash manifest)
                hash12   (subs hash 0 12)
                filename (:filename manifest)
                sqlite3  (sqlite3-init-module #js {:locateFile #(str "/js/" %)})
                pool     (.installOpfsSAHPoolVfs sqlite3
                           #js {:name "opfs-sahpool" :initialCapacity 6})
                _        (when-not (contains? (set (js->clj (.getFileNames pool)))
                                              (pool-filename hash12))
                           (p/let [resp   (js/fetch (str "/dictionary/" filename))
                                   buffer (.arrayBuffer resp)]
                             (.importDb pool (pool-filename hash12) (js/Uint8Array. buffer))))
                DB       (.. sqlite3 -oo1 -DB)
                db       (DB. #js {:filename (pool-filename hash12)
                                   :vfs "opfs-sahpool"})]
          (reset! db-state {:db db :hash hash12})
          (log/info :dictionary-sqlite/ready {:hash hash12}))
        (p/catch (fn [err]
                   (log/error :dictionary-sqlite/init-failed {:error (str err)})
                   (reset! db-state nil))))))


(defn ready? []
  (map? @db-state))


(defn suggest
  "Returns {:suggestions [...] :prefill string-or-nil} from SQLite.
   Falls back to empty when db is not yet ready."
  [input]
  (let [state @db-state]
    (if-not (map? state)
      {:suggestions [] :prefill nil}
      (let [db         (:db state)
            normalized (utils/normalize-german (or input ""))]
        (if (empty? normalized)
          {:suggestions [] :prefill nil}
          (let [prefix-end (str normalized "￿")
                rows       (-> (.exec db #js {:sql         suggest-sql
                                              :bind        #js [normalized normalized prefix-end]
                                              :returnValue "resultRows"
                                              :rowMode     "object"})
                               (js->clj :keywordize-keys true))
                prefill    (->> rows (filter #(pos? (:has_exact %))) first :lemma)]
            {:suggestions (mapv (fn [{:keys [lemma_id lemma pos rank translation]}]
                                  {:lemma-id    lemma_id
                                   :lemma       lemma
                                   :pos         pos
                                   :rank        rank
                                   :translation translation})
                                rows)
             :prefill     prefill}))))))
