(ns client.layering-test
  "The storage abstraction barrier, checked against the source tree.

   Layer 1, the engine (`db`, `db.pouch`, `db.sqlite`, `db-migrations`,
   `sync`, `tasks`), knows documents, databases, indexes and replication.
   Layer 2, the adapters (`adapters.*`), speak domain outward. The learner's
   data is one adapter, `adapters.learner` with its `memory`, `loader` and
   `documents`: it owns the document types and the memory that keeps them.
   Everything above the adapters — use-cases, pages, domain — never sees a
   storage name. Use cases and pages require no adapter at all: they read
   memory through the reads the learner port hands them.

   Presenters sit beside the views: actions, effects and use cases never
   require one."
  (:require
   [cljs.reader :as reader]
   [cljs.test :refer-macros [deftest is]]
   [clojure.string :as str]))


(def ^:private fs (js/require "fs"))


(def ^:private path (js/require "path"))


(def ^:private source-root "src/client")


(def ^:private engine
  '#{db db.pouch db.sqlite db-migrations sync tasks})


(def ^:private may-require-db
  "Who may touch `db` (raw PouchDB) directly: the engine, plus two adapters
   that work on raw documents by nature — the backup moves every document
   in and out unchanged, and identity opens device-db before the engine
   exists, since an incoming credential is handled first."
  (into engine '#{adapters.data-export adapters.identity}))


(defn- adapter?
  [ns-name]
  (str/starts-with? (str ns-name) "adapters."))


(def ^:private wiring
  "Namespaces that start engine components and do nothing else: the
   composition root, and the port that starts and stops the task runner."
  '#{main ports.task-queue})


(defn- may-require-engine?
  "Who may call the engine: the engine itself, the adapters, and the
   wiring that starts it."
  [ns-name]
  (or (engine ns-name) (adapter? ns-name) (wiring ns-name)))


(defn- storage-free?
  "Who must not contain a storage name: everything above the adapters."
  [ns-name]
  (not (or (engine ns-name) (adapter? ns-name))))


(def ^:private storage-tokens
  "Names that belong to a stored document and to nothing above the
   adapters. `\"example\"` is not listed: it is also a lesson trial
   kind, a domain word. `\"vocab:\"` — the content-addressed identity of
   ADR-0008, built by domain.vocabulary — is a different literal from
   `\"vocab\"` and passes on its own."
  [":_id" ":_rev" ":selector" "\"vocab\"" "\"review\"" "\"collection\"" "\"lesson\"" "\"task\""])


(defn- source-files
  [dir]
  (mapcat (fn [entry]
            (let [full (.join path dir entry)]
              (if (.isDirectory (.statSync fs full))
                (source-files full)
                (when (str/ends-with? entry ".cljs")
                  [full]))))
   (.readdirSync fs dir)))


(defn- without-comments
  [text]
  (str/replace text #"(?m);.*$" ""))


(defn- ns-form
  [text]
  (reader/read-string text))


(defn- ns-name-of
  [form]
  (second form))


(defn- requires-of
  [form]
  (->> form
       (filter #(and (seq? %) (= :require (first %))))
       (mapcat rest)
       (map #(if (vector? %) (first %) %))
       set))


(defn- sources
  []
  (for [file (source-files source-root)
        :let [text (.readFileSync fs file "utf8")
              form (ns-form text)]]
    {:file     file
     :ns       (ns-name-of form)
     :requires (requires-of form)
     :text     (without-comments text)}))


(deftest only-the-engine-requires-db
  (doseq [{:keys [file ns requires]} (sources)
          :when (and (contains? requires 'db) (not (may-require-db ns)))]
    (is false (str file " requires db; only the engine and the raw-document adapters may"))))


(deftest only-adapters-and-the-engine-require-the-engine
  (doseq [{:keys [file ns requires]} (sources)
          required requires
          :when    (and (engine required) (not (may-require-engine? ns)))]
    (is false (str file " requires " required "; only adapters, the engine and main may"))))


(deftest nothing-above-the-adapters-names-storage
  (doseq [{:keys [file ns text]} (sources)
          :when (storage-free? ns)
          token storage-tokens
          :when (str/includes? text token)]
    (is false (str file " contains " token "; storage names stop at the adapters"))))


(def ^:private memory-internals
  "How code reaches inside the learner's data in memory. Outside the
   learner's adapter, memory is read through the functions of
   `adapters.learner.memory`, so none of these appears there."
  [":slot-of" ":cards" ":examples-by-word" "get-in memory" "[:learner/memory :"
   "(:words memory" "(:collections memory" "(:examples memory"])


(defn- use-case-or-page?
  [ns-name]
  (let [n (str ns-name)]
    (or (str/starts-with? n "use-cases.") (str/starts-with? n "pages."))))


(deftest use-cases-and-pages-require-no-adapter
  ;; They reach the learner's data, memory reads included, through the
  ;; ports they are handed.
  (doseq [{:keys [file ns requires]} (sources)
          required requires
          :when    (and (use-case-or-page? ns) (adapter? required))]
    (is false (str file " requires " required "; use cases and pages reach adapters through ports"))))


(defn- learner-adapter?
  [ns-name]
  (or (= 'adapters.learner ns-name)
      (str/starts-with? (str ns-name) "adapters.learner.")))


(deftest nothing-outside-the-learner-adapter-reads-inside-memory
  (doseq [{:keys [file ns text]} (sources)
          :when (not (learner-adapter? ns))
          token memory-internals
          :when (str/includes? text token)]
    (is false (str file " contains " token "; memory is read through adapters.learner.memory"))))


(defn- presenter?
  [ns-name]
  (str/ends-with? (str ns-name) ".presenter"))


(defn- must-not-require-presenter?
  "Who does not require a presenter: actions, effects and use cases. A
   presenter maps state to what the view renders; a function or constant these
   need lives with them. `application` is left out: it is the shell, and its
   render sits in the same namespace as its actions and effects."
  [ns-name]
  (let [n (str ns-name)]
    (or (str/starts-with? n "use-cases.")
        (str/ends-with? n ".actions")
        (str/ends-with? n ".effects"))))


(deftest only-views-require-presenters
  (doseq [{:keys [file ns requires]} (sources)
          required requires
          :when    (and (presenter? required) (must-not-require-presenter? ns))]
    (is false (str file " requires " required "; a presenter is for the view only"))))


(deftest the-barrier-test-sees-the-tree
  (let [names (set (map :ns (sources)))]
    (is (contains? names 'db.pouch))
    (is (contains? names 'adapters.learner.documents))
    (is (contains? names 'use-cases.vocabulary))
    (is (contains? names 'pages.words.actions))
    (is (< 40 (count names)))))
