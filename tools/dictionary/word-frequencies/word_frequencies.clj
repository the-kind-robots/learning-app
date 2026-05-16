(ns word-frequencies
  "Normalize the SUBTLEX-DE cleaned XLSX into the frequency.tsv schema.

   Source file: `SUBTLEX-DE cleaned version with Zipf values.xlsx` (OSF/UGent).
   Two columns of interest:
     Word     — German wordform.
     SUBTLEX  — normalized count: occurrences per million tokens in the
                SUBTLEX corpus (~25M subtitle tokens).

   The file also has WFfreqcount (raw counts), CUMfreqcount, ZipfSUBTLEX
   (log10 scale), and Google-corpus equivalents (Google00*, ZipfGoogle).
   We pick the per-million SUBTLEX value because:
     - SUBTLEX (subtitles) fits conversational vocabulary better than
       Google's web/book mix.
     - A linear normalized count is straightforward to aggregate across
       surface-form variants downstream (sum is well-defined). Zipf is
       log-scale and would need logsumexp.

   Why hand-roll XLSX parsing?
   The file is one sheet with ~190k rows; XLSX is just a ZIP of XML, so we
   read it with the JDK's ZipFile + StAX rather than pulling Apache POI
   (~10MB) for a one-shot import.

   Shared strings ARE required: column A (Word) is encoded as t=\"s\" with
   sharedStrings indices. Numeric columns are inline.

   Output schema (TSV):
     word<TAB>count<TAB>source
  "
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   (java.util.zip ZipFile)
   (javax.xml.stream XMLInputFactory XMLStreamConstants XMLStreamReader)))


(def ^:private word-header "Word")


;; SUBTLEX-DE also offers ZipfSUBTLEX (log10 scale) and Google-corpus columns
;; (Google00pm, ZipfGoogle). We deliberately choose the linear per-million
;; SUBTLEX value — see ns docstring for the rationale.
(def ^:private count-header "SUBTLEX")


(defn- header-indices
  "Locate exact-match Word/SUBTLEX header positions; throw if either missing."
  [headers]
  (let [find-idx (fn [label]
                   (first (keep-indexed (fn [i v]
                                          (when (= label (str/trim (str v))) i))
                                        headers)))
        word-idx (find-idx word-header)
        cnt-idx  (find-idx count-header)]
    (when-not word-idx
      (throw (ex-info (str "Cannot find " word-header " column") {:headers headers})))
    (when-not cnt-idx
      (throw (ex-info (str "Cannot find " count-header " column") {:headers headers})))
    {:word word-idx :count cnt-idx}))


(defn- xml-reader
  ^XMLStreamReader
  [stream]
  (.createXMLStreamReader (XMLInputFactory/newFactory) stream))


(defn- start-element?
  "True when reader is sitting on a <tag-name> open tag."
  [^XMLStreamReader reader tag-name]
  (and (= XMLStreamConstants/START_ELEMENT (.getEventType reader))
       (= tag-name (.getLocalName reader))))


(defn- end-element?
  "True when reader is sitting on a </tag-name> close tag."
  [^XMLStreamReader reader tag-name]
  (and (= XMLStreamConstants/END_ELEMENT (.getEventType reader))
       (= tag-name (.getLocalName reader))))


(defn- column-letter->index
  "Convert XLSX cell reference letters (A1, AB12) to 0-based column index."
  [cell-ref]
  (let [letters (re-find #"[A-Z]+" (str cell-ref))]
    (dec (reduce (fn [acc ch]
                   (+ (* acc 26) (- (int ch) (int \A)) 1))
                 0
                 letters))))


(defn- read-shared-strings
  "Read xl/sharedStrings.xml into a vector of strings.
   Each <si> may contain multiple <t> runs (rich text), so we accumulate."
  [^ZipFile zip-file]
  (with-open [stream (.getInputStream zip-file (.getEntry zip-file "xl/sharedStrings.xml"))]
    (let [reader  (xml-reader stream)
          strings (volatile! [])
          current (volatile! nil)]
      (try
        (while (.hasNext reader)
          (.next reader)
          (cond
            (start-element? reader "si")
            (vreset! current (StringBuilder.))

            (and (start-element? reader "t") @current)
            (.append ^StringBuilder @current (.getElementText reader))

            (end-element? reader "si")
            (do
              (vswap! strings conj (str @current))
              (vreset! current nil))))
        @strings
        (finally
         (.close reader))))))


(defn- cell-value
  [shared-strings cell-type raw-value]
  ;; t="s" means raw-value is an index into sharedStrings; otherwise inline literal.
  (if (= cell-type "s")
    (get shared-strings (parse-long raw-value) "")
    raw-value))


(defn- row->values
  "Materialize a sparse {col-idx -> value} cell map into a dense vector."
  [row]
  (mapv #(get row %) (range (inc (reduce max 0 (keys row))))))


(defn- parse-count
  [value]
  (when-not (str/blank? value)
    (try
      (parse-double (str/trim value))
      (catch Exception _ nil))))


(defn- normalize-row
  [columns values]
  (let [word  (str/trim (str (get values (:word columns) "")))
        count (parse-count (get values (:count columns) ""))]
    (when (and (seq word) count (pos? count))
      {:word word :count count :source "subtlex-de"})))


(defn- read-frequency-rows
  "Walk sheet1.xml once. The first <row> sets headers (validated immediately,
   so a bad header file fails before scanning the rest); later rows are
   projected straight into {:word :count :source} entries.

   Volatile state machine: `row` accumulates cells of the current <row>,
   `cell` accumulates index/type/value of the current <c>, `columns` caches
   the resolved word/count column indexes after the header row."
  [^ZipFile zip-file shared-strings]
  (with-open [stream (.getInputStream zip-file (.getEntry zip-file "xl/worksheets/sheet1.xml"))]
    (let [reader  (xml-reader stream)
          columns (volatile! nil)
          row     (volatile! nil)
          cell    (volatile! nil)
          rows    (transient [])]
      (try
        (while (.hasNext reader)
          (.next reader)
          (cond
            (start-element? reader "row")
            (vreset! row {})

            (start-element? reader "c")
            (vreset! cell {:index (column-letter->index (.getAttributeValue reader nil "r"))
                           :type  (.getAttributeValue reader nil "t")})

            (and (start-element? reader "v") @cell)
            (vswap! cell assoc :value (cell-value shared-strings
                                                  (:type @cell)
                                                  (.getElementText reader)))

            (and (end-element? reader "c") @cell)
            (do
              (vswap! row assoc (:index @cell) (:value @cell))
              (vreset! cell nil))

            (end-element? reader "row")
            (let [values (row->values @row)]
              (if (nil? @columns)
                (vreset! columns (header-indices values))
                (when-let [entry (normalize-row @columns values)]
                  (conj! rows entry)))
              (vreset! row nil))))
        (persistent! rows)
        (finally
         (.close reader))))))


(defn normalize-xlsx
  "Return normalized rows from SUBTLEX-DE xlsx: [{:word w :count n :source \"subtlex-de\"} ...]."
  [path]
  (with-open [zip-file (ZipFile. (io/file path))]
    (let [shared-strings (read-shared-strings zip-file)
          rows           (read-frequency-rows zip-file shared-strings)]
      (->> rows
           (sort-by (juxt (comp - :count) :word))
           vec))))


(defn write-frequency-tsv!
  [output rows]
  (io/make-parents output)
  (with-open [writer (io/writer output)]
    (.write writer "word\tcount\tsource\n")
    (doseq [{:keys [word count source]} rows]
      (.write writer (str word "\t" count "\t" source "\n")))))


(defn generate
  [{:keys [input output]
    :or   {output "../../resources/dictionary/frequency.tsv"}}]
  (let [rows (normalize-xlsx input)]
    (write-frequency-tsv! output rows)
    (println (format "Wrote %,d SUBTLEX-DE frequency rows to %s" (count rows) output))))


(defn- parse-cli-args
  [args]
  (loop [remaining args
         opts      {}]
    (case (first remaining)
      nil        opts
      "--input"  (recur (nnext remaining) (assoc opts :input (second remaining)))
      "--output" (recur (nnext remaining) (assoc opts :output (second remaining)))
      (throw (ex-info (str "Unexpected argument: " (first remaining))
                      {:args args})))))


(defn -main
  [& args]
  (let [{:keys [input output] :as opts} (parse-cli-args args)]
    (when (or (str/blank? input) (str/blank? output))
      (throw (ex-info "Usage: word-frequencies --input PATH --output PATH" {:args args})))
    (generate opts)))
