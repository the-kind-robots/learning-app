(ns client.layering-test
  "The storage abstraction barrier, checked against the source tree.

   Layer 1, the engine (`db`, `db.pouch`, `db.sqlite`, `db-migrations`,
   `sync`, `tasks`), knows documents, databases, indexes, views and
   replication. Layer 2, the repositories (`adapters.*`), own their document
   type, shape, indexes and views, and speak domain outward. Everything
   above them — use-cases, pages, domain — never sees a storage name."
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
  "Who may call the engine: the engine itself, the repositories, and the
   wiring that starts it."
  [ns-name]
  (or (engine ns-name) (adapter? ns-name) (wiring ns-name)))


(defn- storage-free?
  "Who must not contain a storage name: everything above the repositories."
  [ns-name]
  (not (or (engine ns-name) (adapter? ns-name))))


(def ^:private storage-tokens
  "Names that belong to a stored document and to nothing above the
   repositories. `\"example\"` is not listed: it is also a lesson trial
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


(deftest only-repositories-and-the-engine-require-the-engine
  (doseq [{:keys [file ns requires]} (sources)
          required requires
          :when    (and (engine required) (not (may-require-engine? ns)))]
    (is false (str file " requires " required "; only adapters, the engine and main may"))))


(deftest nothing-above-the-repositories-names-storage
  (doseq [{:keys [file ns text]} (sources)
          :when (storage-free? ns)
          token storage-tokens
          :when (str/includes? text token)]
    (is false (str file " contains " token "; storage names stop at the adapters"))))


(deftest the-barrier-test-sees-the-tree
  (let [names (set (map :ns (sources)))]
    (is (contains? names 'db.pouch))
    (is (contains? names 'adapters.words))
    (is (contains? names 'use-cases.vocabulary))
    (is (< 40 (count names)))))
