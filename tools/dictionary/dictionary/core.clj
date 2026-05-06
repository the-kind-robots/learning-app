(ns dictionary.core
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dictionary.download :as download]
            [dictionary.emit :as emit]
            [dictionary.frequency :as frequency]
            [dictionary.goethe :as goethe]
            [dictionary.kaikki :as kaikki]
            [dictionary.transform :as transform]
            [utils :refer [now-iso]]))



(defn slim-entry
  "Strip a parsed Kaikki entry down to only the fields needed for transformation.
   Dramatically reduces memory footprint when holding entries in the merge map."
  [entry]
  {:word         (:word entry)
   :pos          (:pos entry)
   :tags         (:tags entry)
   :senses       (mapv (fn [s]
                         (cond-> {:tags    (vec (distinct (filter string? (:tags s))))
                                  :glosses (vec (distinct (filter string? (:glosses s))))}
                           (:form_of s) (assoc :form_of (:form_of s))))
                       (:senses entry))
   :translations (filterv #(= "ru" (:lang_code %)) (:translations entry))
   :forms        (filterv :form (mapv #(select-keys % [:form :article]) (:forms entry)))})


(defn merge-kaikki-entries
  "Merge duplicate Kaikki entries (same word+pos) by unioning translations and forms."
  [existing new-entry]
  (-> existing
      (update :translations (fn [old] (vec (distinct (concat old (:translations new-entry))))))
      (update :forms (fn [old] (vec (distinct (concat old (:forms new-entry))))))
      (update :senses (fn [old] (vec (distinct (concat old (:senses new-entry))))))))


(defn merge-entries-from-lines
  "Pure function: given a seq of raw JSON strings, filter German lemma entries,
   merge duplicates by [word pos]. Returns {:entries {[word pos] → entry},
   :total-lines n, :parse-errors n}."
  [lines]
  (reduce (fn [acc ^String line]
            (when (zero? (mod (:total-lines acc) 10000))
              (print (format "\r  Lines read: %,d " (:total-lines acc)))
              (flush))
            (let [acc (update acc :total-lines inc)]
              ;; Quick string check: skip lines that aren't German
              (if-not (re-find #"\"lang_code\"\s*:\s*\"de\"" line)
                acc
                (try
                  (let [entry (json/parse-string line true)]
                    (if-not (and (kaikki/german-entry? entry)
                                 (kaikki/lemma-entry? entry))
                      acc
                      (let [k    [(str/lower-case (:word entry)) (str/lower-case (or (:pos entry) "unknown"))]
                            slim (slim-entry entry)]
                        (update acc
                                :entries
                                (fn [m]
                                  (if-let [existing (get m k)]
                                    (assoc m k (merge-kaikki-entries existing slim))
                                    (assoc m k slim)))))))
                  (catch Exception _
                    (update acc :parse-errors inc))))))
          {:entries {} :total-lines 0 :parse-errors 0}
          lines))


(defn- read-and-merge-entries
  "Pass 1: Stream Kaikki gz, filter German lemma entries, merge duplicates.
   Thin I/O wrapper around merge-entries-from-lines."
  [kaikki-path]
  (println "  Pass 1: Reading & merging Kaikki entries...")
  (with-open [reader (kaikki/open-gz-reader kaikki-path)]
    (let [result (merge-entries-from-lines (line-seq reader))]
      (println (format "  Lines read: %,d. Unique lemma entries: %,d (parse errors: %,d)"
                       (:total-lines result)
                       (count (:entries result))
                       (:parse-errors result)))
      result)))


(defn transform-entries
  "Pure function: transform merged entries into dictionary docs and surface-form index.
   Returns {:docs [...], :sf-index {...}, :entry-count n, :skip-count n, :cefr-counts {...}}."
  ([merged-entries goethe-index timestamp]
   (transform-entries merged-entries goethe-index timestamp nil))
  ([merged-entries goethe-index timestamp frequency-index]
   (let [sorted (sort-by (fn [e] [(:word e) (:pos e)]) (vals merged-entries))]
     (-> (reduce (fn [acc kentry]
                   (let [processed (+ (:entry-count acc) (:skip-count acc))]
                     (when (zero? (mod processed 10000))
                       (print (format "\r  Entries processed: %,d " processed))
                       (flush)))
                   (let [dict-entry (transform/dictionary-entry kentry goethe-index timestamp frequency-index)]
                     (if dict-entry
                       (-> acc
                           (update :docs conj dict-entry)
                           (update :sf-index transform/accumulate-surface-forms dict-entry)
                           (update :entry-count inc)
                           (update :cefr-counts update (get-in dict-entry [:meta :cefr-level]) (fnil inc 0)))
                       (update acc :skip-count inc))))
                 {:docs        []
                  :sf-index    {}
                  :entry-count 0
                  :skip-count  0
                  :cefr-counts {"a1" 0 "a2" 0 "b1" 0 nil 0}}
                 sorted)
         (update :docs #(sort-by (juxt (comp - :rank) :value) %))))))


(defn- transform-and-write-entries
  "Pass 2: Transform merged entries, write JSONL. Thin I/O wrapper around transform-entries."
  [merged-entries goethe-index timestamp frequency-index output-dir]
  (println "  Pass 2: Transforming & writing dictionary entries...")
  (let [entries-path (str output-dir "/dictionary-entries.jsonl")
        {:keys [docs sf-index entry-count skip-count cefr-counts]}
        (transform-entries merged-entries goethe-index timestamp frequency-index)
        file-stats   (emit/write-jsonl! entries-path docs)]
    (println (format "  Dictionary entries written: %,d (skipped: %,d)"
                     entry-count
                     skip-count))
    {:file-stats  file-stats
     :sf-index    sf-index
     :entry-count entry-count
     :skip-count  skip-count
     :cefr-counts cefr-counts}))


(defn- process-kaikki-stream
  "Stream Kaikki gz, filter & merge entries, write dictionary entries to JSONL,
   write surface forms, write manifest."
  [kaikki-path goethe-index timestamp frequency-index output-dir]
  (println "Processing Kaikki stream...")
  (let [{:keys [entries total-lines]} (read-and-merge-entries kaikki-path)
        {:keys [file-stats sf-index entry-count skip-count cefr-counts]}
        (transform-and-write-entries entries goethe-index timestamp frequency-index output-dir)]

    ;; Write surface forms
    (println (format "  Surface form index size: %,d normalized forms" (count sf-index)))
    (println "  Writing surface forms...")
    (let [sf-docs  (transform/surface-form-documents sf-index)
          sf-stats (emit/write-jsonl! (str output-dir "/surface-forms.jsonl") sf-docs)]
      (println (format "  Surface forms written: %,d" (:count sf-stats)))

      ;; Write manifest
      (println "  Writing manifest...")
      (emit/write-manifest! output-dir
                            {"dictionary-entries.jsonl" file-stats
                             "surface-forms.jsonl"      sf-stats}
                            timestamp)

      ;; Print summary
      (println "\n=== Ingestion Summary ===")
      (println (format "  Total Kaikki lines:      %,d" total-lines))
      (println (format "  Dictionary entries:      %,d" entry-count))
      (println (format "  Skipped (disallowed POS):%,d" skip-count))
      (println (format "  Surface forms:           %,d" (:count sf-stats)))
      (println "  CEFR breakdown:")
      (doseq [[level cnt] (sort-by key cefr-counts)]
        (println (format "    %-4s  %,d" (or level "none") cnt)))
      (println "========================\n"))))


(defn build
  [{:keys [output-dir data-dir frequency-file]
    :or   {data-dir "data" output-dir "resources/dictionary"}}]
  (let [timestamp (now-iso)]
    (println "Dictionary ingestion starting at" timestamp)
    (.mkdirs (io/file data-dir))
    (.mkdirs (io/file output-dir))
    (let [paths (download/download-sources! data-dir)]
      (println "Building Goethe CEFR index...")
      (let [goethe-index (goethe/stem-level-index (:goethe paths))]
        (println (format "  Goethe stems loaded: %,d" (count goethe-index)))
        (let [frequency-index (frequency/read-frequency-file frequency-file)]
          (when (seq frequency-index)
            (println (format "  Frequency entries loaded: %,d" (count frequency-index))))
          (process-kaikki-stream (:kaikki paths) goethe-index timestamp frequency-index output-dir))))
    (println "Done.")
    (shutdown-agents)))
