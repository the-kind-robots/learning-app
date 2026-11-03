#!/usr/bin/env bb
;; Fails when the release build renames a JS method or property the client calls (#697, #699).
;;
;; Closure renames every property name that no extern declares, across the whole build. A name the
;; browser owns then no longer matches, and the call throws only in the release build, which no suite
;; runs. docs/dev/cljs-interop-renaming.md says which forms keep a name.
;;
;; It builds `:app` in release into target/release-names and reads the property map Closure writes
;; (`.shadow-cljs/builds/app/release/closure.property.map`, a line `name:renamed` per renamed property).
;; It reports each interop name of the sources the map holds; clj-kondo's analysis finds them.
;; Run from the repository root:
;;
;;   bb scripts/release_renames.clj
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])


(def output-dir "target/release-names")


(def build
  (p/shell {:out :string :err :string :continue true}
           "npx"
           "shadow-cljs"
           "release"
           "app"
           "--config-merge" (str "{:output-dir \"" output-dir "\"}")))


(when-not (zero? (:exit build))
  (println (:out build) (:err build))
  (println "The release build failed.")
  (System/exit 1))


(def sources
  "The project's files the bundle was built from: the inputs the build's manifest lists, found under the
   source paths of shadow-cljs.edn. Libraries and node_modules are under none of them."
  (let [source-paths (:source-paths (edn/read-string (slurp "shadow-cljs.edn")))]
    (->> (edn/read-string (slurp (str output-dir "/manifest.edn")))
         (mapcat :sources)
         (keep (fn [resource]
                 (some #(let [file (str % "/" resource)] (when (fs/exists? file) file)) source-paths)))
         distinct)))


(def uses
  "Each interop name of `sources` with the places that use it. clj-kondo reads every interop form:
   `(.m x)`, `(.-f x)`, `(. x m)`, `(. x -f)`, the members of `(.. x -a (b))`, threaded ones."
  (let [{:keys [out]} (apply p/shell
                             {:out :string :continue true}
                             "clj-kondo"
                             "--config"
                             "{:output {:format :edn} :analysis {:instance-invocations true}}"
                             "--lint"
                             sources)]
    (->> (:instance-invocations (:analysis (edn/read-string out)))
         ;; The JVM side of a .cljc file is not in the bundle.
         (remove #(= :clj (:lang %)))
         ;; A .cljc file is analysed once per language: count each site once.
         (map (fn [{:keys [filename method-name name-row]}]
                [(str/replace method-name #"^-" "") (str filename ":" name-row)]))
         distinct
         (reduce (fn [uses [method-name place]]
                   (update uses method-name (fnil conj []) place))
                 {}))))


(def renamed
  (->> (keys uses)
       (filter (into #{}
                     (map #(first (str/split % #":" 2)))
                     (str/split-lines (slurp ".shadow-cljs/builds/app/release/closure.property.map"))))
       sort))


(if (empty? renamed)
  (println "No interop name is renamed by the release build.")
  (do (println "The release build renames these names; the call throws in production:")
      (doseq [name renamed]
        (println (str "  ." name "  " (str/join " " (get uses name)))))
      (println "Call them through a ^js local or js-invoke: docs/dev/cljs-interop-renaming.md.")
      (System/exit 1)))
