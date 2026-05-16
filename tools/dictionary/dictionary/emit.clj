(ns dictionary.emit
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [utils :as utils])
  (:import
   [java.security MessageDigest]
   [java.io FileInputStream]))

(defn lemma->json-line
  "Transform kebab-case keys to snake_case, serialize as JSON string."
  [lemma]
  (json/generate-string (utils/transform-keys lemma)))

(defn write-jsonl!
  "Write seq of lemmas as JSONL to path. Returns {:count n :bytes b}."
  [path lemmas]
  (let [f (io/file path)
        _ (io/make-parents f)]
    (with-open [w (io/writer f)]
      (loop [lemmas lemmas
             n      0
             b      0]
        (if-let [lemma (first lemmas)]
          (let [line       (lemma->json-line lemma)
                line-bytes (+ (count (.getBytes line "UTF-8")) 1)]
            (.write w line)
            (.write w "\n")
            (recur (rest lemmas) (inc n) (+ b line-bytes)))
          {:count n :bytes b})))))

(defn sha256
  "Compute SHA-256 hex digest of a file."
  [path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [fis (FileInputStream. (str path))]
      (loop []
        (let [n (.read fis buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (let [hash-bytes (.digest digest)]
      (apply str (map #(format "%02x" %) hash-bytes)))))

(defn write-manifest!
  "Write manifest.edn with counts, byte sizes, and SHA-256 hashes."
  [output-dir file-stats timestamp]
  (let [manifest-path (str output-dir "/manifest.edn")
        files         (into {}
                            (map (fn [[filename stats]]
                                   (let [fpath (str output-dir "/" filename)]
                                     [filename (assoc stats :sha256 (sha256 fpath))]))
                                 file-stats))
        manifest      {:generated-at timestamp
                       :files        files}]
    (spit manifest-path (str (pr-str manifest) "\n"))
    (println "  Wrote manifest:" manifest-path)
    manifest))
