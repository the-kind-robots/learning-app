(ns dictionary.core
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dictionary.download :as download]
            [dictionary.emit :as emit]
            [dictionary.frequency :as frequency]
            [dictionary.goethe :as goethe]
            [dictionary.kaikki :as kaikki]
            [dictionary.sqlite :as sqlite]
            [dictionary.transform :as transform]
            [utils]))


(set! *warn-on-reflection* true)


(defn slim-entry
  [entry]
  (let [pos (str/lower-case (:pos entry "unknown"))]
    {:word         (:word entry)
     :pos          (:pos entry)
     :tags         (:tags entry)
     :canonical-value (when (= "noun" pos) (kaikki/canonical-noun-form entry))
     :senses       (mapv (fn [s]
                           (cond-> {:tags    (vec (distinct (filter string? (:tags s))))
                                    :glosses (vec (distinct (filter string? (:glosses s))))}
                             (:form_of s) (assoc :form_of (:form_of s))))
                         (:senses entry))
     :translations (filterv #(= "ru" (:lang_code %)) (:translations entry))
     :forms        (->> (:forms entry)
                        (filter kaikki/inflectional-form?)
                        (mapv #(select-keys % [:form :article]))
                        (filterv :form))}))


(defn merge-dump-entries
  [existing new-entry]
  (-> existing
      (update :translations (fn [old] (vec (distinct (concat old (:translations new-entry))))))
      (update :forms (fn [old] (vec (distinct (concat old (:forms new-entry))))))
      (update :senses (fn [old] (vec (distinct (concat old (:senses new-entry))))))))


(defn- classify-line
  [^String line]
  (if-not (re-find #"\"lang_code\"\s*:\s*\"de\"" line)
    [:skip]
    (try
      (let [entry (json/parse-string line true)]
        (if (and (kaikki/german-entry? entry) (kaikki/lemma-entry? entry))
          (let [pos       (str/lower-case (:pos entry "unknown"))
                entry-key [(str/lower-case (:word entry)) pos
                           (if (= "noun" pos)
                             (kaikki/extract-article entry)
                             (kaikki/entry-discriminant entry pos))]]
            [:ok entry-key (slim-entry entry)])
          [:skip]))
      (catch Exception _ [:parse-error]))))


(defn entries-from-lines
  [lines]
  (let [result (reduce (fn [acc line]
                         (when (zero? (mod (:total-lines acc) 10000))
                           (print (format "\r  Lines read: %,d " (:total-lines acc)))
                           (flush))
                         (let [acc (update acc :total-lines inc)
                               [tag key slim] (classify-line line)]
                           (case tag
                             :skip        acc
                             :parse-error (update acc :parse-errors inc)
                             :ok          (update acc
                                                  :dump-entries
                                                  (fn [dump-entries]
                                                    (if-let [existing (get dump-entries key)]
                                                      (assoc dump-entries key (merge-dump-entries existing slim))
                                                      (assoc dump-entries key slim)))))))
                       {:dump-entries {} :total-lines 0 :parse-errors 0}
                       lines)]
    (update result :dump-entries vals)))


(defn- load-dump-entries
  [kaikki-path]
  (println "  Reading & merging Kaikki entries...")
  (with-open [^java.io.BufferedReader reader (kaikki/open-gz-reader kaikki-path)]
    (let [result (entries-from-lines (line-seq reader))]
      (println
       (format
        "  Lines read: %,d. Unique lemmas: %,d (parse errors: %,d)"
        (:total-lines result)
        (count (:dump-entries result))
        (:parse-errors result)))
      result)))


(defn build-lemmas
  [dump-entries goethe-index frequency-index]
  (let [step       (fn [acc dump-entry]
                     (when (zero? (mod (count acc) 10000))
                       (print (format "\r  Lemmas processed: %,d " (count acc)))
                       (flush))
                     (if-let [lemma (transform/lemma dump-entry goethe-index frequency-index)]
                       (conj acc lemma)
                       acc))
        lemmas     (reduce step [] dump-entries)
        skip-count (- (count dump-entries) (count lemmas))]
    [lemmas skip-count]))


(defn- build-artifacts!
  [kaikki-path goethe-index frequency-index timestamp output-dir]
  (println "Building dictionary artifacts...")
  (let [;; Obtain lemmas
        {:keys [dump-entries total-lines]} (load-dump-entries kaikki-path)
        _ (println "  Building lemmas...")
        [lemmas skip-count] (build-lemmas dump-entries goethe-index frequency-index)
        _ (println (format "  Lemmas: %,d (skipped: %,d)" (count lemmas) skip-count))

        ;; Write dictionary
        _ (println "  Writing dictionary.sqlite...")
        stats (sqlite/write-dictionary! output-dir lemmas)
        _ (println (format "  dictionary.sqlite: %,d bytes" (:bytes stats)))

        ;; Write data metadata useful for enrichment
        _ (println "  Writing enrichment-meta.jsonl...")
        meta-stats (sqlite/emit-enrichment-meta! output-dir lemmas)]

    (println (format "  enrichment-meta.jsonl: %,d bytes" (:bytes meta-stats)))
    (println "  Writing manifest...")
    (emit/write-manifest! output-dir
                          {(:name stats) {:count (count lemmas)
                                          :bytes (:bytes stats)}}
                          timestamp)
    (println "\n=== Ingestion Summary ===")
    (println (format "  Total Kaikki lines: %,d" total-lines))
    (println (format "  Lemmas written:     %,d" (count lemmas)))
    (println (format "  Skipped (bad POS):  %,d" skip-count))
    (println (format "  Client dictionary:  %s" (:name stats)))
    (println "========================\n")))


(defn apply-enrichment!
  [{:keys [output-dir]
    :or   {output-dir "resources/dictionary"}}]
  (println "Applying enrichment to dictionary.sqlite in" output-dir "...")
  (sqlite/apply-enrichment! output-dir)
  (let [manifest    (edn/read-string (slurp (str output-dir "/manifest.edn")))
        lemma-count (get-in manifest [:files "dictionary.sqlite" :count])
        sqlite-file (io/file (str output-dir "/dictionary.sqlite"))]
    (emit/write-manifest! output-dir
                          {"dictionary.sqlite" {:count lemma-count
                                                :bytes (.length sqlite-file)}}
                          (utils/now-iso)))
  (println "Done."))


(defn build
  [{:keys [output-dir data-dir frequency-file]
    :or   {data-dir "data" output-dir "resources/dictionary"}}]
  (let [frequency-file  (or frequency-file (str data-dir "/frequency.tsv"))
        timestamp       (utils/now-iso)
        ;; Downloading source data
        _ (println "Dictionary ingestion starting at" timestamp)
        _ (.mkdirs (io/file data-dir))
        _ (.mkdirs (io/file output-dir))
        paths           (download/download-sources! data-dir)

        ;; Building word importance indexes
        _ (println "Building Goethe CEFR index...")
        goethe-index    (goethe/stem-level-index (:goethe paths))
        _ (println (format "  Goethe stems loaded: %,d" (count goethe-index)))

        frequency-index (frequency/read-frequency-file frequency-file)
        _ (println (format "  Frequency entries loaded: %,d" (count frequency-index)))

        ;; Building the dictionary
        _ (build-artifacts! (:kaikki paths) goethe-index frequency-index timestamp output-dir)

        ;; If an enrichment-output side-file is present in output-dir, apply it
        ;; so the shipped dictionary has user-facing translations populated.
        ;; The patch is idempotent and gap-fill only (see specs/dictionary-storage).
        enrichment-file (io/file (str output-dir "/enrichment-output.jsonl"))
        _ (when (.exists enrichment-file)
            (apply-enrichment! {:output-dir output-dir}))
        _ (println "Done.")]
    (shutdown-agents)))
