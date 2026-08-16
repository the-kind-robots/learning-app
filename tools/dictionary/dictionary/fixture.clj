(ns dictionary.fixture
  "Builds the small dictionary the browser suite serves (#289).

   Reuses the production emitters on purpose: the point of a fixture is to be
   the shipped artifact in miniature, and a hand-written schema would drift
   from the one the client queries the moment either moves."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [dictionary.emit :as emit]
   [dictionary.sqlite :as sqlite]
   [utils]))


(set! *warn-on-reflection* true)


(defn build
  [{:keys [source output-dir]
    :or   {source     "test/fixtures/dictionary/lemmas.edn"
           output-dir "target/test-dictionary"}}]
  ;; write-dictionary! deletes and renames dictionary.sqlite inside output-dir.
  ;; Pointed at resources/ that would destroy the shipped artifact.
  (when (str/includes? (str output-dir) "resources/dictionary")
    (throw (ex-info "the fixture build refuses to write over the shipped dictionary"
                    {:output-dir output-dir})))
  (.mkdirs (io/file output-dir))
  (let [lemmas (edn/read-string (slurp source))
        stats  (sqlite/write-dictionary! output-dir lemmas)]
    (emit/write-manifest! output-dir
                          {(:name stats) {:count (count lemmas)
                                          :bytes (:bytes stats)}}
                          (utils/now-iso))
    (println (format "  Fixture dictionary: %,d lemmas, %,d bytes -> %s"
                     (count lemmas)
                     (:bytes stats)
                     output-dir))))
