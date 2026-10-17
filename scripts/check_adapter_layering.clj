#!/usr/bin/env bb
;; An adapter reaches no adapter of another top-level name: `adapters.X...` may
;; require `adapters.X...` and nothing under `adapters.Y`. What it needs from
;; elsewhere comes in through its port, as an argument or a capability.
;; clj-kondo's ns groups cannot say "a different segment", so this reads the
;; namespace usages clj-kondo finds and checks the one thing they cannot.
;; Every adapter is covered by default; none is registered. The rest of the
;; layering (only ports require adapters at all) is in
;; .clj-kondo/layering/config.edn. Run from the repository root:
;;
;;   bb scripts/check_adapter_layering.clj [dir ...]     (default: src/client)
(require '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])


(defn adapter-family
  "The segment after `adapters.` in `ns-name`, or nil when it is no adapter."
  [ns-name]
  (when-let [[_ family] (re-find #"^adapters\.([^.]+)" (str ns-name))]
    family))


(defn violations
  [dirs]
  (let [{:keys [out]} (apply p/shell
                             {:out :string :continue true}
                             "clj-kondo"
                             "--config"
                             "{:output {:format :edn} :analysis {:namespace-usages true}}"
                             "--lint" dirs)
        usages        (-> out edn/read-string :analysis :namespace-usages)]
    (->> usages
         (filter (fn [{:keys [from to]}]
                   (let [a (adapter-family from)
                         b (adapter-family to)]
                     (and a b (not= a b)))))
         (map (juxt :filename :from :to))
         distinct
         sort)))


(let [dirs  (or (seq *command-line-args*) ["src/client"])
      found (violations dirs)]
  (doseq [[file from to] found]
    (println (str file
                  ": "
                  from
                  " requires "
                  to
                  ", an adapter of another name; it comes in through the port")))
  (if (seq found)
    (System/exit 1)
    (println "adapters: no adapter requires another")))
