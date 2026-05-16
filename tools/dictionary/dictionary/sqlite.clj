(ns dictionary.sqlite
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dictionary.emit :as emit]
            [dictionary.materialize :as materialize]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [utils :refer [transform-keys]]))


(defn- configure-for-bulk-write!
  [conn]
  (doseq [pragma ["PRAGMA journal_mode = OFF"
                  "PRAGMA synchronous = OFF"
                  "PRAGMA cache_size = -65536"]]
    (jdbc/execute! conn [pragma])))


(defn- create-tables!
  [conn]
  ;; text_id is a build-pipeline join key only — absent from the shipped artifact.
  (jdbc/execute!
   conn
   ["CREATE TABLE lemmas (
        id    INTEGER PRIMARY KEY,
        value TEXT    NOT NULL,
        pos   TEXT,
        rank  INTEGER NOT NULL)"])
  ;; WITHOUT ROWID: hot path is WHERE lemma_id = ? ORDER BY rank —
  ;; a free clustered B-tree scan on the composite PK.
  (jdbc/execute!
   conn
   ["CREATE TABLE translations (
        lemma_id INTEGER NOT NULL REFERENCES lemmas(id),
        rank     INTEGER NOT NULL,
        value    TEXT    NOT NULL,
        PRIMARY KEY (lemma_id, rank)
      ) WITHOUT ROWID"])
  (jdbc/execute! conn
                 ["CREATE TABLE labels (
        id   INTEGER PRIMARY KEY,
        name TEXT    NOT NULL UNIQUE)"])
  ;; (lemma_id, rank) mirrors the translations PK; prefix lookup is free.
  (jdbc/execute!
   conn
   ["CREATE TABLE translation_labels (
        lemma_id INTEGER NOT NULL,
        rank     INTEGER NOT NULL,
        label_id INTEGER NOT NULL REFERENCES labels(id),
        FOREIGN KEY (lemma_id, rank) REFERENCES translations(lemma_id, rank),
        PRIMARY KEY (lemma_id, rank, label_id)
      ) WITHOUT ROWID"])
  ;; WITHOUT ROWID: normalized_form is the leading PK column — lookups are clustered.
  (jdbc/execute!
   conn
   ["CREATE TABLE surface_forms (
        normalized_form TEXT    NOT NULL,
        lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
        PRIMARY KEY (normalized_form, lemma_id)
      ) WITHOUT ROWID"]))


(defn- insert-lemmas!
  [tx row-seq]
  (with-open [ps (jdbc/prepare tx ["INSERT INTO lemmas (id, value, pos, rank) VALUES (?, ?, ?, ?)"])]
    (jdbc/execute-batch! ps (map (juxt :id :value :pos :rank) row-seq) {:batch-size 1000})))


(defn- insert-translations!
  [tx row-seq]
  (with-open [ps (jdbc/prepare tx ["INSERT INTO translations (lemma_id, rank, value) VALUES (?, ?, ?)"])]
    (jdbc/execute-batch! ps (map (juxt :lemma-id :rank :value) row-seq) {:batch-size 2000})))


(defn- insert-surface-forms!
  [tx row-seq]
  (with-open [ps (jdbc/prepare tx ["INSERT INTO surface_forms (normalized_form, lemma_id) VALUES (?, ?)"])]
    (jdbc/execute-batch! ps (map (juxt :normalized-form :lemma-id) row-seq) {:batch-size 5000})))


(defn- insert-labels!
  [tx row-seq]
  (with-open [ps (jdbc/prepare tx ["INSERT INTO labels (id, name) VALUES (?, ?)"])]
    (jdbc/execute-batch! ps (map (juxt :id :name) row-seq) {:batch-size 100})))


(defn- insert-translation-labels!
  [tx row-seq]
  (with-open [ps (jdbc/prepare tx ["INSERT INTO translation_labels (lemma_id, rank, label_id) VALUES (?, ?, ?)"])]
    (jdbc/execute-batch! ps (map (juxt :lemma-id :rank :label-id) row-seq) {:batch-size 5000})))


(defn- finalize!
  "Rename tmp file to out-filename and return stats."
  [output-dir tmp-path out-filename]
  (let [out-path (str output-dir "/" out-filename)
        out-file (io/file out-path)]
    (.delete out-file)
    (.renameTo (io/file tmp-path) out-file)
    {:name  out-filename
     :hash  (emit/sha256 out-path)
     :bytes (.length out-file)}))


(defn write-dictionary!
  [output-dir input-lemmas]
  (let [tmp-path (str output-dir "/dictionary.tmp.sqlite")
        {:keys [lemmas translations labels translation-labels surface-forms]}
        (materialize/build-table-data input-lemmas)]
    (println (format "  Surface forms: %,d" (count surface-forms)))
    (println (format "  Labels: %,d  Translation labels: %,d" (count labels) (count translation-labels)))
    (.delete (io/file tmp-path))
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname tmp-path})]
      (configure-for-bulk-write! conn)
      (create-tables! conn)
      (println "  Writing dictionary.sqlite...")
      (jdbc/with-transaction [tx conn]
        (insert-lemmas!             tx lemmas)
        (insert-translations!       tx translations)
        (insert-labels!             tx labels)
        (insert-translation-labels! tx translation-labels)
        (insert-surface-forms!      tx surface-forms)))
    (let [stats (finalize! output-dir tmp-path "dictionary.sqlite")]
      (assoc stats
             :lemma-count (count lemmas)
             :sf-count    (count surface-forms)
             :label-count (count labels)
             :tl-count    (count translation-labels)))))


(defn emit-enrichment-meta!
  [output-dir lemmas]
  (let [path (str output-dir "/enrichment-meta.jsonl")]
    (with-open [writer (io/writer path)]
      (doseq [entry lemmas]
        (let [enrichment (:enrichment entry {})
              record     {:id          (:text-id entry)
                          :value       (:value entry)
                          :pos         (:pos entry)
                          :forms_count (count (:forms entry []))
                          :meta        (transform-keys
                                        {:senses      (get enrichment :senses [])
                                         :cefr-level  (:cefr-level enrichment)
                                         :sense-count (:sense-count enrichment)})}]
          (.write writer ^String (json/generate-string record))
          (.write writer "\n"))))
    {:name  "enrichment-meta.jsonl"
     :bytes (.length (io/file path))}))


(defn- lemma-key
  "Strips optional discriminant suffix: 'lemma:word:pos:disc' → 'lemma:word:pos'."
  [text-id]
  (str/join ":" (take 3 (str/split text-id #":"))))


(defn- read-enrichment
  [path]
  (with-open [reader (io/reader path)]
    (into {}
          (keep (fn [line]
                  (let [{:keys [id translation]} (json/parse-string line true)]
                    (when (and id (seq translation))
                      [id translation]))))
          (line-seq reader))))


(defn lemma:key->id
  [conn & {:as opts}]
  (into {} (map (fn [{:keys [value pos id]}]
                  [(str "lemma:" (str/lower-case value) ":" pos) id]))
        (jdbc/execute! conn ["SELECT id, value, pos FROM lemmas"] opts)))

(defn label:name->id
  [conn & {:as opts}]
  (into {} (map (juxt :name :id))
        (jdbc/execute! conn ["SELECT id, name FROM labels"] opts)))


(defn already-translated
  [conn & {:as opts}]
  (into #{} (map :lemma_id)
        (jdbc/execute! conn ["SELECT DISTINCT lemma_id FROM translations"] opts)))


(defn- missing-translations
  [enrichment already-translated lemma-key->id]
  (->> (for [[text-id patch-translations] enrichment

             :let [lemma-id (lemma-key->id (lemma-key text-id))]
             :when (and lemma-id (not (already-translated lemma-id)))

             [idx translation] (map-indexed vector patch-translations)]

         (materialize/normalize-translation
          {:lemma-id lemma-id
           :rank     (inc idx)
           :value    (:value translation)
           :labels   (get-in translation [:meta :labels])}))
       (materialize/dedupe-translations)
       seq))


(defn- translation-labels
  [translations label->id]
  (for [{:keys [lemma-id rank labels]} translations
        :when  labels
        label  labels
        :let   [label-id (label->id label)]
        :when  label-id]
    {:lemma-id lemma-id :rank rank :label-id label-id}))


(defn apply-enrichment!
  "Patch dictionary.sqlite with translations from enrichment-output.jsonl.
   Gap-fill: only inserts translations for lemmas that have none.
   New labels from enrichment are added to the labels table."
  [output-dir]
  (let [sqlite-path (str output-dir "/dictionary.sqlite")
        jsonl-path  (str output-dir "/enrichment-output.jsonl")
        _           (when-not (.exists (io/file jsonl-path))
                      (throw (ex-info "enrichment-output.jsonl not found" {:path jsonl-path})))
        enrichment  (read-enrichment jsonl-path)
        _           (println (format "  Patch records: %,d" (count enrichment)))]

    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname sqlite-path})]
      (let [conn (jdbc/with-options conn {:builder-fn rs/as-unqualified-maps})

            lemma-key->id      (lemma:key->id conn)
            label-name->id     (label:name->id conn)
            already-translated (already-translated conn)

            ;; Extract missing translations and new labels
            translations (missing-translations enrichment already-translated lemma-key->id)
            labels       (->> translations
                              (mapcat :labels)
                              (remove nil?)
                              (distinct)
                              (keep #(when-not (label-name->id %) {:name %})))

            ;; Report stats
            unmatched (count (remove #(lemma-key->id (lemma-key %)) (keys enrichment)))
            _         (println (format "  Unmatched: %,d  Inserting: %,d translations  New labels: %,d"
                                       unmatched (count translations) (count labels)))]
        ;; Update tables
        (jdbc/with-transaction+options [tx conn]
          (insert-translations! tx translations)
          (insert-labels!       tx labels)
          ;; Update translations -> labels connection
          (let [label-name->id     (label:name->id tx)
                translation-labels (translation-labels translations label-name->id)]
            (insert-translation-labels! tx translation-labels)))))))

