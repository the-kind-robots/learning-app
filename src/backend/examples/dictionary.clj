(ns examples.dictionary
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [utils :as utils]))


;; =============================================================================
;; The dictionary file
;; =============================================================================


(defn configured-dictionary-dir
  "Filesystem directory to read and serve the dictionary from, when set.
   Production leaves it unset and answers from its own classpath resource — the
   only artifact a deployment can be held to. The browser suite points it at a
   small fixture (#289) so a spec waits milliseconds for the SQLite worker
   instead of importing 37 MB into a fresh OPFS on every test."
  [env]
  (some-> (get env "LEARNING_APP__DICTIONARY_DIR") not-empty io/file))


(def ^:private resource-name "dictionary/dictionary.sqlite")


(def ^:private resource-file
  "The classpath dictionary as a file. A jar entry cannot be opened by JDBC, so
   it is copied out once per process."
  (delay
   (if-let [url (io/resource resource-name)]
     (if (= "file" (.getProtocol url))
       (io/file url)
       (let [copy (java.io.File/createTempFile "dictionary" ".sqlite")]
         (.deleteOnExit copy)
         (with-open [in (io/input-stream url)]
           (io/copy in copy))
         copy))
     (throw (ex-info "Dictionary file not found on the classpath"
                     {:resource resource-name})))))


(defn- dictionary-file
  [env]
  (if-let [dir (configured-dictionary-dir env)]
    (io/file dir "dictionary.sqlite")
    @resource-file))


(defonce ^:private connection (atom nil))


(defn open!
  "Points lookups at the dictionary file named by `env`, over one read-only
   connection, and reads it once. Throws, naming the file, when it is missing or
   is not a dictionary: a broken dictionary must not pass for words the
   dictionary does not know."
  [env]
  (let [file (dictionary-file env)]
    (when-not (.isFile file)
      (throw (ex-info (str "Dictionary file not found: " file) {:file (str file)})))
    (let [opened (jdbc/get-connection {:jdbcUrl   (str "jdbc:sqlite:" (.getAbsolutePath file))
                                       :open_mode 1})]
      (try
        (jdbc/execute-one! opened ["SELECT cefr_level FROM lemmas LIMIT 1"])
        (jdbc/execute-one! opened ["SELECT 1 FROM surface_forms LIMIT 1"])
        (catch Exception error
          (.close opened)
          (throw (ex-info (str "Dictionary file unreadable: " file)
                          {:file (str file)} error))))
      (when-let [previous (first (reset-vals! connection opened))]
        (.close ^java.sql.Connection previous))
      opened)))


(defn- query
  "Rows for `sql-params`. The one connection serves every request thread, and a
   JDBC connection is not safe for concurrent use, so queries take turns."
  [sql-params]
  (let [opened (or @connection (open! (System/getenv)))]
    (locking opened
      (jdbc/execute! opened sql-params {:builder-fn rs/as-unqualified-lower-maps}))))


;; =============================================================================
;; Lookup by form
;; =============================================================================


(defn- lemmas-of-form
  "Lemma rows whose surface forms include `form`, by the form index."
  [form]
  (query ["SELECT l.id, l.value, l.pos, l.cefr_level
           FROM surface_forms s JOIN lemmas l ON l.id = s.lemma_id
           WHERE s.normalized_form = ?
           ORDER BY l.rank DESC, l.id
           LIMIT 20"
          form]))


(defn- translations-by-lemma
  [ids]
  (if (empty? ids)
    {}
    (->> (query (into [(str "SELECT lemma_id, value FROM translations WHERE lemma_id IN ("
                            (str/join "," (repeat (count ids) "?"))
                            ") ORDER BY lemma_id, rank")]
                      ids))
         (group-by :lemma_id))))


(defn- entry
  "A lemma row in the shape callers read."
  [translations {:keys [id value pos cefr_level]}]
  {:id          id
   :value       value
   :pos         pos
   :translation (mapv (fn [row] {:lang "ru" :value (:value row)})
                      (get translations id))
   :meta        {:cefr_level cefr_level}})


(defn- merge-dictionary-entry-docs
  [& doc-groups]
  (->> doc-groups
       (remove nil?)
       (apply concat)
       (utils/distinct-by :id)
       (vec)))


;; =============================================================================
;; Normalization
;; =============================================================================


(defn- normalize-dictionary-form
  [form]
  (some-> form str/trim str/lower-case))


(defn- normalize-russian-text
  [text]
  (some-> text
          utils/sanitize-text
          str/lower-case
          utils/non-blank))


(defn- strip-article
  [form]
  (some-> form
          str/trim
          (str/replace #"(?iu)^(der|die|das)\s+" "")))


(defn- strip-reflexive
  [form]
  (some-> form
          str/trim
          (str/replace #"(?iu)^sich\s+" "")))


(defn- canonical-dictionary-form
  [form]
  (some-> form
          normalize-dictionary-form
          strip-article
          strip-reflexive))


;; =============================================================================
;; Form matching
;; =============================================================================


(defn has-article?
  "Returns true if word is prefixed with a German definite article (der/die/das)."
  [word]
  (boolean
   (re-matches #"(?iu)(der|die|das)\s+.+" (or word ""))))


(defn same-lemma?
  "Returns true if dictionary-form refers to the same lemma as word.
   Article-bearing words (e.g. \"die Leiter\") require an exact match so that
   \"der Leiter\" and \"die Leiter\" stay distinct. Article-free words strip
   articles and reflexive pronouns before comparing, so \"vorstellen\" matches
   \"sich vorstellen\"."
  [word dictionary-form]
  (let [target (normalize-dictionary-form word)
        dictionary-form (normalize-dictionary-form dictionary-form)]
    (when (and target dictionary-form)
      (if (has-article? word)
        ;; When the target includes an article (e.g. "die Leiter"), require an
        ;; exact match so that "der Leiter" and "die Leiter" stay distinct.
        (= dictionary-form target)
        ;; Without an article, strip articles and reflexive pronouns from both
        ;; sides before comparing so that "vorstellen" matches "sich vorstellen".
        (= (canonical-dictionary-form dictionary-form)
           (canonical-dictionary-form target))))))


;; =============================================================================
;; Translation matching
;; =============================================================================


(defn- translation-variant-set
  [translation]
  (->> (str/split (or translation "") #"[;,/]")
       (map normalize-russian-text)
       (remove nil?)
       set))


(defn- translation-match?
  [expected actual]
  (boolean
   (seq (set/intersection
         (translation-variant-set expected)
         (translation-variant-set actual)))))


;; =============================================================================
;; Dictionary lookup
;; =============================================================================


(defn- exact-form?
  "Whether the lemma is written as `key`: with its article when `key` carries
   one, with or without it when `key` is bare."
  [key article? row]
  (let [written (utils/normalize-german (:value row))]
    (or (= key written)
        (and (not article?)
             (= key (utils/normalize-german (strip-article (:value row))))))))


(defn lookup-dictionary-entries
  "Dictionary entries for `dictionary-form`, those written exactly so first, then
   those reached by a surface form. A form with an article finds only its own
   lemma. Throws when the dictionary cannot be read."
  [dictionary-form]
  (let [form-key (some-> dictionary-form utils/normalize-german utils/non-blank)]
    (when form-key
      (let [article?   (has-article? dictionary-form)
            candidates (lemmas-of-form form-key)
            exact      (filterv #(exact-form? form-key article? %) candidates)
            chosen     (if article?
                         exact
                         (merge-dictionary-entry-docs exact candidates))
            by-lemma   (translations-by-lemma (mapv :id chosen))]
        (mapv #(entry by-lemma %) chosen)))))


(defn- dictionary-entry-matches-gloss?
  [entry translation]
  (some #(and (= "ru" (:lang %))
              (translation-match? translation (:value %)))
        (:translation entry)))


;; =============================================================================
;; Public API
;; =============================================================================


(defn lookup-word-meta
  "Part of speech and CEFR level of `word`, preferring an entry whose gloss
   is one of `translations`. Nil when the dictionary has no entry; throws when
   it cannot be read."
  [word translations]
  (let [entries (lookup-dictionary-entries word)
        matches-any-gloss? (fn [entry]
                             (some #(dictionary-entry-matches-gloss? entry %)
                                   translations))]
    (when (seq entries)
      (let [best (or (first (filter matches-any-gloss? entries))
                     (first entries))]
        {:partOfSpeech (:pos best)
         :cefrLevel    (get-in best [:meta :cefr_level])}))))


(defn lemma-in-structure?
  "Returns true if structure contains at least one item whose dictionaryForm
   is the same lemma as word."
  [word structure]
  (boolean
   (some #(same-lemma? word (:dictionaryForm %)) structure)))
