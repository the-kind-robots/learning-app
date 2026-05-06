(ns dictionary.import
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [db :as db]
            [malli.core :as m])
  (:import [java.io FileInputStream]
           [java.net URI]
           [java.security MessageDigest])
  (:gen-class))


(def ^:private default-db "dictionary-db")


(def ^:private default-batch-size 1000)


(def ^:private default-max-bytes 5242880)


(def ^:private meta-doc-id "dictionary-meta")


(def ^:private schema-version 2)


(def ^:private dictionary-epoch "dictionary-v2-raw-lemma-ids")


(def ^:private cli-options
  [["-i" "--input-dir PATH" "Directory containing dictionary files"]
   ["-d" "--db NAME" "CouchDB database name" :default default-db]
   ["-b" "--batch N" "Batch size" :default default-batch-size :parse-fn parse-long]
   ["-m" "--max-bytes BYTES" "Max bytes per batch" :default default-max-bytes :parse-fn parse-long]
   [nil "--reset" "Reset database before import"]
   [nil "--dry-run" "Validate files and print summary, then exit"]
   ["-h" "--help" "Show help"]])


(defn- exit!
  [code message]
  (binding [*out* *err*]
    (println message))
  (System/exit code))


(defn- usage
  [summary]
  (str
   "Usage: dictionary-import --input-dir PATH [--db NAME] [--batch N] [--max-bytes BYTES] [--reset]\n\n"
   "Options:\n"
   summary))


(defn- parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        missing-input? (str/blank? (:input-dir options))]
    (cond
      (:help options) (exit! 0 (usage summary))
      (seq errors)    (exit! 1 (str (str/join "\n" errors) "\n\n" (usage summary)))
      (seq arguments) (exit! 1
                             (str "Unexpected arguments: " (str/join " " arguments)
                                  "\n\n" (usage summary)))
      missing-input?  (exit! 1 (str "Missing required argument: --input-dir\n\n" (usage summary)))
      :else           options)))


(defn- ensure-env
  [key]
  (let [value (System/getenv key)]
    (when (str/blank? value)
      (exit! 1 (str "Missing required environment variable: " key)))
    value))


(defn- db-connection
  []
  (let [url    (ensure-env "COUCHDB_URL")
        uri    (URI. url)
        scheme (or (.getScheme uri) "http")
        host   (.getHost uri)
        port   (let [p (.getPort uri)]
                 (if (neg? p)
                   (if (= scheme "https") 443 80)
                   p))
        user   (or (System/getenv "COUCHDB_USER") "admin")
        pass   (ensure-env "COUCHDB_PASS")]
    (when (str/blank? host)
      (exit! 1 "COUCHDB_URL must include host"))
    {:scheme   scheme
     :host     host
     :port     port
     :username user
     :password pass}))


(defn- ensure-db!
  [conn db-name reset?]
  (let [db-ref   {:name db-name :conn conn}
        existed? (db/exists? conn db-name)]
    (when (and reset? existed?)
      @(db/destroy db-ref))
    (let [db (db/use conn db-name)]
      ;; Allow public read access (empty members = no auth required for reads)
      (db/secure db {:admins  {:names [] :roles []}
                     :members {:names [] :roles []}})
      db)))


(defn- validate-input!
  [input-dir]
  (when (str/blank? input-dir)
    (exit! 1 "Missing required argument: --input-dir"))
  (let [dir (io/file input-dir)]
    (when-not (.isDirectory dir)
      (exit! 1 (str "Input directory not found: " input-dir)))))


(defn- read-manifest
  [input-dir]
  (let [manifest-path (str input-dir "/manifest.edn")
        f (io/file manifest-path)]
    (when-not (.exists f)
      (exit! 1 (str "Missing manifest: " manifest-path)))
    {:path manifest-path
     :data (edn/read-string (slurp f))}))


(defn- manifest-files
  [manifest]
  (get-in manifest [:files]))


(defn- hex
  [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))


(defn- sha256-file
  [path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [fis (FileInputStream. (str path))]
      (loop []
        (let [n (.read fis buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (hex (.digest digest))))


(defn- sha256-string
  [s]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (hex (.digest digest (.getBytes s "UTF-8")))))


(defn- canonical
  [value]
  (cond
    (map? value)
    (into (sorted-map)
          (map (fn [[k v]] [k (canonical v)]))
          value)

    (vector? value)
    (mapv canonical value)

    (seq? value)
    (mapv canonical value)

    :else
    value))


(defn- doc-digest
  [doc]
  (sha256-string (json/generate-string (canonical (dissoc doc :_rev)))))


(defn- diff-doc
  "Return nil when unchanged; otherwise return doc ready for CouchDB upsert."
  [existing-docs-by-id new-doc]
  (let [{:keys [rev digest]} (get existing-docs-by-id (:_id new-doc))]
    (when (not= digest (doc-digest new-doc))
      (cond-> new-doc
        rev (assoc :_rev rev)))))


(defn- bulk!
  [op db docs]
  (if (empty? docs)
    0
    (let [results  @(op db docs)
          failures (filter :error results)]
      (when (seq failures)
        (exit! 1 (format "%d error(s). First 3:\n%s"
                         (count failures) (pr-str (take 3 failures)))))
      (- (count results) (count failures)))))


(defn- jsonl-batches
  [reader batch-size max-bytes]
  (letfn [(step [lines]
            (lazy-seq
              (loop [ls lines, batch [], bytes 0]
                (if-let [line (first ls)]
                  (let [doc    (json/parse-string line true)
                        ; inc accounts for the stripped newline
                        bytes' (+ bytes (inc (count (.getBytes ^String line "UTF-8"))))]
                    (if (or (>= (count batch) (dec batch-size))
                            (>= bytes' max-bytes))
                      (cons (conj batch doc) (step (rest ls)))
                      (recur (rest ls) (conj batch doc) bytes')))
                  (when (seq batch)
                    (list batch))))))]
    (step (remove str/blank? (line-seq reader)))))


(defn- import-jsonl!
  [db file-path batch-size max-bytes]
  (println (str "Importing " file-path "..."))
  (with-open [reader (io/reader file-path)]
    (let [total   (atom 0)
          batches (jsonl-batches reader batch-size max-bytes)]
      (doseq [batch batches]
        (swap! total + (bulk! db/bulk-docs db batch)))
      @total)))


(defn- upsert-from-file!
  [db file-path db-index batch-size max-bytes]
  (println (str "Diffing " file-path "..."))
  (with-open [reader (io/reader file-path)]
    (let [stats   (atom {:seen 0 :upserted 0 :skipped 0 :ids #{}})
          batches (jsonl-batches reader batch-size max-bytes)]
      (doseq [docs-from-file batches]
        (let [changes (keep #(diff-doc db-index %) docs-from-file)
              ok      (bulk! db/bulk-docs db changes)]
          (swap! stats (fn [s]
                         (-> s
                             (update :ids into (map :_id docs-from-file))
                             (update :seen + (count docs-from-file))
                             (update :upserted + ok)
                             (update :skipped + (- (count docs-from-file) (count changes))))))))
      @stats)))


(defn- ours-docs?
  [id]
  (or (= id meta-doc-id)
      (str/starts-with? id "lemma:")
      (str/starts-with? id "sf:")))


(defn- delete-candidates
  "Return generated docs present in CouchDB but absent from new dictionary payload."
  [existing-docs-by-id new-dictionary-ids]
  (->> existing-docs-by-id
       (keep (fn [[id {:keys [rev]}]]
               (when (and (ours-docs? id)
                          (not (contains? new-dictionary-ids id)))
                 {:_id id :_rev rev})))))


(defn- delete-stale-docs!
  "Delete generated Couch docs missing from the new dictionary."
  [db existing-docs-by-id new-dictionary-ids]
  (let [removals (delete-candidates existing-docs-by-id new-dictionary-ids)]
    (println (format "Deleting %,d stale managed docs..." (count removals)))
    (bulk! db/bulk-delete db removals)))


(defn- meta-doc
  [manifest-data manifest-sha-value files]
  {:_id              meta-doc-id
   :type             "dictionary-meta"
   :schema-version   schema-version
   :dictionary-epoch dictionary-epoch
   :generated-at     (:generated-at manifest-data)
   :enriched-at      (:enriched-at manifest-data)
   :manifest-sha256  manifest-sha-value
   :files            files})


(defn- update-meta!
  [db db-index data manifest-sha-value files]
  (pos? (bulk! db/bulk-docs db (keep #(diff-doc db-index %) [(meta-doc data manifest-sha-value files)]))))


(defn- validate-file-hash!
  [file-path expected-sha]
  (let [actual (sha256-file file-path)]
    (when (not= expected-sha actual)
      (exit! 1
             (format "SHA-256 mismatch for %s\n  expected: %s\n  actual:   %s"
                     file-path
                     expected-sha
                     actual)))))


(defn- validate-file-count!
  [file-path expected-count]
  (with-open [reader (io/reader file-path)]
    (let [actual (count (remove str/blank? (line-seq reader)))]
      (when (not= expected-count actual)
        (exit! 1
               (format "Line count mismatch for %s\n  expected: %d\n  actual:   %d"
                       file-path
                       expected-count
                       actual))))))


(defn- duplicate-doc-ids
  [file-path]
  (with-open [reader (io/reader file-path)]
    (loop [lines      (line-seq reader)
           seen       #{}
           duplicates #{}
           result     []]
      (if-let [line (first lines)]
        (if (str/blank? line)
          (recur (rest lines) seen duplicates result)
          (let [doc (try (json/parse-string line true)
                         (catch Exception e
                           (exit! 1 (format "Invalid JSON in %s: %s" file-path (.getMessage e)))))
                id  (:_id doc)]
            (cond
              (not (string? id))
              (exit! 1 (format "Document missing string _id in %s" file-path))

              (contains? duplicates id)
              (recur (rest lines) seen duplicates result)

              (contains? seen id)
              (recur (rest lines) seen (conj duplicates id) (conj result id))

              :else
              (recur (rest lines) (conj seen id) duplicates result))))
        result))))


(defn- validate-unique-ids!
  [file-path]
  (let [duplicates (duplicate-doc-ids file-path)]
    (when (seq duplicates)
      (exit! 1
             (format "Duplicate _id values in %s: %s"
                     file-path
                     (str/join ", " (take 20 duplicates)))))))


(def ^:private lemma-schema
  [:map
   [:_id         [:re #"^lemma:.+"]]
   [:type        [:string {:min 1}]]
   [:value       [:string {:min 1}]]
   [:translation [:vector [:map
                            [:lang  [:string {:min 1}]]
                            [:value [:string {:min 1}]]]]]])


(def ^:private surface-schema
  [:map
   [:_id     [:re #"^sf:.+"]]
   [:value   [:string {:min 1}]]
   [:entries [:vector {:min 1}
              [:map
               [:lemma-id [:re #"^lemma:.+"]]
               [:lemma    [:string {:min 1}]]
               [:rank     int?]]]]])


(defn- validate-sample-docs!
  [file-path schema]
  (with-open [reader (io/reader file-path)]
    (doseq [line (->> (line-seq reader) (remove str/blank?) (take 5))]
      (let [doc (try (json/parse-string line true)
                     (catch Exception e
                       (exit! 1 (format "Invalid JSON in %s: %s" file-path (.getMessage e)))))]
        (when-not (m/validate schema doc)
          (exit! 1 (format "Invalid doc in %s:\n%s"
                           file-path
                           (m/explain schema doc))))))))


(defn- unresolved-translation-count
  [file-path]
  (with-open [reader (io/reader file-path)]
    (reduce (fn [count line]
              (if (str/blank? line)
                count
                (let [doc (json/parse-string line true)]
                  (if (empty? (:translation doc))
                    (inc count)
                    count))))
            0
            (line-seq reader))))


(defn- validate-jsonl!
  "Pre-flight validation: check SHA, line count, and sample doc structure for both JSONL files."
  [input-dir files]
  (let [lemmas-file   (get files "dictionary-entries.jsonl")
        lemmas-path   (str input-dir "/dictionary-entries.jsonl")
        surfaces-file (get files "surface-forms.jsonl")
        surfaces-path (str input-dir "/surface-forms.jsonl")]
    (println "Validating JSONL files...")
    (validate-file-hash! lemmas-path (:sha256 lemmas-file))
    (validate-file-count! lemmas-path (:count lemmas-file))
    (validate-unique-ids! lemmas-path)
    (validate-sample-docs! lemmas-path lemma-schema)
    (let [unresolved (unresolved-translation-count lemmas-path)]
      (when (pos? unresolved)
        (println (format "  Warning: %,d dictionary entr%s unresolved (empty translation)."
                         unresolved
                         (if (= 1 unresolved) "y is" "ies are")))))
    (validate-file-hash! surfaces-path (:sha256 surfaces-file))
    (validate-file-count! surfaces-path (:count surfaces-file))
    (validate-unique-ids! surfaces-path)
    (validate-sample-docs! surfaces-path surface-schema)
    (println "  Validation passed.")))


(defn- dictionary-paths
  [input-dir]
  {:lemmas   (str input-dir "/dictionary-entries.jsonl")
   :surfaces (str input-dir "/surface-forms.jsonl")})


(defn- validate-manifest-files!
  [files]
  (when-not (and (map? files)
                 (get files "dictionary-entries.jsonl")
                 (get files "surface-forms.jsonl"))
    (exit! 1 "Manifest is missing required file entries.")))


(defn- dry-run!
  [files db-name]
  (let [lemma-stats   (get files "dictionary-entries.jsonl")
        surface-stats (get files "surface-forms.jsonl")]
    (println "\n=== Dry Run Summary ===")
    (println (format "  Dictionary entries: %,d (%,d bytes)" (:count lemma-stats) (:bytes lemma-stats)))
    (println (format "  Surface forms:      %,d (%,d bytes)" (:count surface-stats) (:bytes surface-stats)))
    (println (format "  Target database:    %s" db-name))
    (println "  Validation passed. No data was uploaded.")
    (println "========================")
    (System/exit 0)))


(defn- compare-meta
  [meta manifest-sha]
  (and (= schema-version   (:schema-version meta))
       (= dictionary-epoch (:dictionary-epoch meta))
       (= manifest-sha     (:manifest-sha256 meta))))


(defn- db-state
  [db-ref manifest-sha-value]
  (let [info        @(db/info db-ref)
        doc-count   (:doc-count info)
        meta        (when (pos? doc-count) @(db/get db-ref meta-doc-id))
        up-to-date? (and meta (compare-meta meta manifest-sha-value))]
    {:info        info
     :doc-count   doc-count
     :meta        meta
     :up-to-date? up-to-date?}))


(defn- write-metrics!
  [input-dir manifest-data db-info]
  (let [path    (str input-dir "/import-metrics.json")
        payload {:generated_at (:generated-at manifest-data)
                 :enriched_at  (:enriched-at manifest-data)
                 :doc_count    (:doc-count db-info)
                 :data_size    (:data-size db-info)
                 :disk_size    (:disk-size db-info)
                 :files        (:files manifest-data)}]
    (spit path (json/generate-string payload {:pretty true}))
    (println (str "Wrote metrics: " path))))


(defn- fresh-import!
  [{:keys [db-ref paths data manifest-sha-value files batch max-bytes input-dir]}]
  (let [entry-count (import-jsonl! db-ref (:lemmas paths) batch max-bytes)
        form-count  (import-jsonl! db-ref (:surfaces paths) batch max-bytes)]
    @(db/insert db-ref (meta-doc data manifest-sha-value files))
    (let [info         @(db/info db-ref)
          actual-count (:doc-count info)
          expected     (+ entry-count form-count 1)]
      (when (not= expected actual-count)
        (exit! 1 (format "Post-upload count mismatch: expected %d, got %d" expected actual-count)))
      (println (format "Import complete. Entries: %,d, Surface forms: %,d" entry-count form-count))
      (write-metrics! input-dir data info))))


(defn- read-db-index
  [db]
  (println "Reading existing docs...")
  (let [{:keys [rows]} @(db/all-docs db {:include_docs true})]
    (into {}
      (for [{:keys [id doc value]} rows]
        [id {:rev    (:rev value)
             :digest (doc-digest doc)}]))))


(defn- diff-import!
  [{:keys [db-ref paths data manifest-sha-value files batch max-bytes input-dir]}]
  (let [db-index           (read-db-index db-ref)
        system-doc-count   (count (remove (comp ours-docs? key) db-index))
        lemma-diff         (upsert-from-file! db-ref (:lemmas paths) db-index batch max-bytes)
        surface-diff       (upsert-from-file! db-ref (:surfaces paths) db-index batch max-bytes)
        meta-updated?      (update-meta! db-ref db-index data manifest-sha-value files)
        seen-ids           (into (:ids lemma-diff) (conj (:ids surface-diff) meta-doc-id))
        deleted-doc-count  (delete-stale-docs! db-ref db-index seen-ids)
        db-info            @(db/info db-ref)
        actual-doc-count   (:doc-count db-info)
        expected-doc-count (+ (get-in files ["dictionary-entries.jsonl" :count])
                              (get-in files ["surface-forms.jsonl" :count])
                              1
                              system-doc-count)]
    (when (not= actual-doc-count expected-doc-count)
      (exit! 1 (format "Post-diff count mismatch: expected %d, got %d" expected-doc-count actual-doc-count)))
    (println (format "Entries seen: %,d (upserted: %,d, skipped: %,d)"
                     (:seen lemma-diff) (:upserted lemma-diff) (:skipped lemma-diff)))
    (println (format "Surface forms seen: %,d (upserted: %,d, skipped: %,d)"
                     (:seen surface-diff) (:upserted surface-diff) (:skipped surface-diff)))
    (println (format "Docs deleted: %,d" deleted-doc-count))
    (println (format "Meta updated: %s" meta-updated?))
    (write-metrics! input-dir data db-info)))


(defn- import-dictionary!
  [{:keys [db-ref manifest-sha-value] :as ctx}]
  (let [{:keys [doc-count up-to-date?]} (db-state db-ref manifest-sha-value)]
    (cond
      up-to-date?
      (do
        (println "Dictionary DB already up to date. Skipping import.")
        (System/exit 0))

      (zero? doc-count)
      (fresh-import! ctx)

      :else
      (diff-import! ctx))))


(defn- manifest-sha
  [manifest-path]
  (sha256-file manifest-path))


(defn upload
  [{:keys [input-dir db batch max-bytes reset dry-run]
    :or   {input-dir "resources/dictionary"
           db        default-db
           batch     default-batch-size
           max-bytes default-max-bytes}}]
  (validate-input! input-dir)
  (let [conn               (db-connection)
        {:keys [path data]} (read-manifest input-dir)
        manifest-sha-value (manifest-sha path)
        files              (manifest-files data)
        paths              (dictionary-paths input-dir)]
    (validate-manifest-files! files)
    (validate-jsonl! input-dir files)
    (when dry-run (dry-run! files db))
    (let [db-ref (ensure-db! conn db reset)]
      (import-dictionary! {:db-ref             db-ref
                           :paths              paths
                           :data               data
                           :manifest-sha-value manifest-sha-value
                           :files              files
                           :batch              batch
                           :max-bytes          max-bytes
                           :input-dir          input-dir}))))


(defn -main
  [& args]
  (let [{:keys [input-dir db batch max-bytes reset dry-run]} (parse-args args)]
    (upload {:input-dir input-dir :db db :batch batch
             :max-bytes max-bytes :reset reset :dry-run dry-run})))
