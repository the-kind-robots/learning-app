#!/usr/bin/env bb
;; Two rules about adapters that clj-kondo's own config cannot state for every adapter at once:
;;
;; - An adapter reaches no adapter of another top-level name: `adapters.X...` may require `adapters.X...` and
;;   nothing under `adapters.Y`. What it needs from elsewhere comes in through its port, as an argument or a
;;   capability. clj-kondo's ns groups cannot say "a different segment".
;; - An adapter is required under its name, the last segment: `[adapters.account :as account]`, in its own port
;;   too, never `adapter` (.claude/rules/clojure-style.md, Namespace aliases). A test may call the namespace it
;;   tests `sut`. clj-kondo's `:consistent-alias` would need every adapter listed by hand.
;;
;; This reads the namespace usages clj-kondo finds and checks both. Every adapter is covered by default; none
;; is registered. The rest of the layering (only ports require adapters at all) is in
;; .clj-kondo/layering/config.edn. Run from the repository root:
;;
;;   bb scripts/check_adapter_layering.clj [dir ...]     (default: src/client test/client)
(require '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])


(defn adapter-family
  "The segment after `adapters.` in `ns-name`, or nil when it is no adapter."
  [ns-name]
  (when-let [[_ family] (re-find #"^adapters\.([^.]+)" (str ns-name))]
    family))


(defn usages
  "The namespace usages clj-kondo finds in `dirs`."
  [dirs]
  (let [{:keys [out]} (apply p/shell
                             {:out :string :continue true}
                             "clj-kondo"
                             "--config"
                             "{:output {:format :edn} :analysis {:namespace-usages true}}"
                             "--lint" dirs)]
    (-> out edn/read-string :analysis :namespace-usages)))


(defn crossings
  "Each `[file from to]` where an adapter requires an adapter of another name."
  [usages]
  (->> usages
       (filter (fn [{:keys [from to]}]
                 (let [a (adapter-family from)
                       b (adapter-family to)]
                   (and a b (not= a b)))))
       (map (juxt :filename :from :to))
       distinct
       sort))


(defn misnamed
  "Each `[file to alias]` where an adapter is required under another name than its last segment, or `sut`."
  [usages]
  (->> usages
       (filter (fn [{:keys [alias to]}]
                 (and alias
                      (adapter-family to)
                      (not (#{"sut" (last (str/split (str to) #"\."))} (str alias))))))
       (map (juxt :filename :to :alias))
       distinct
       sort))


(let [dirs    (or (seq *command-line-args*) ["src/client" "test/client"])
      found   (usages dirs)
      crossed (crossings found)
      named   (misnamed found)]
  (doseq [[file from to] crossed]
    (println (str file ": " from " requires " to ", an adapter of another name; it comes in through the port")))
  (doseq [[file to alias] named]
    (println (str file ": " to " is required as " alias "; require it as " (last (str/split (str to) #"\.")))))
  (if (or (seq crossed) (seq named))
    (System/exit 1)
    (println "adapters: none requires another, each is required under its name")))
