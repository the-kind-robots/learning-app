(ns i18n.files
  "The language files, read when the code compiles."
  (:require
   [clojure.java.io :as io]))


(set! *warn-on-reflection* true)


(defmacro dictionary-texts
  "Each `i18n/<language>.edn` in a source folder, `{language text}`, read when the code compiles: a jar
   carries no source files. A new file reaches a watch build only when `i18n` compiles again, so
   shadow-cljs keeps it out of its cache (`:cache-blockers`)."
  []
  (into {}
        (comp (filter #(= "file" (.getProtocol ^java.net.URL %)))
              (mapcat #(file-seq (io/file %)))
              (keep (fn [^java.io.File file]
                      (when-let [[_ language] (re-matches #"([a-z]{2})\.edn" (.getName file))]
                        [language (slurp file)]))))
        (enumeration-seq (.getResources (.getContextClassLoader (Thread/currentThread)) "i18n"))))
