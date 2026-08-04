(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.build.api :as b])
  (:import
   [java.security MessageDigest]))


(def target-dir "target")


(def class-dir "target/classes")


(def uber-file "target/learning-app.jar")


(def dictionary-class-dir "target/dictionary-classes")


(def dictionary-uber-file "target/dictionary-import.jar")


;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))


(def dictionary-basis (delay (b/create-basis {:project "deps.edn" :aliases [:dictionary-import]})))


(defn clean
  [_]
  (println (format "Cleaning %s..." target-dir))
  (b/delete {:path "target"}))


(defn- assets-digest
  "sha256 over every file under `dir`, in path order.

   This belongs to the build rather than to the server: it needs to walk a
   directory, and inside a jar there is none — only a flat list of entries. The
   server reads the answer back as a single named resource, which a jar does
   serve."
  [dir]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (doseq [^java.io.File file (sort (file-seq (io/file dir)))
            :when (.isFile file)]
      (with-open [stream (io/input-stream file)]
        (let [buffer (byte-array 8192)]
          (loop []
            (let [n (.read stream buffer)]
              (when (pos? n)
                (.update digest buffer 0 n)
                (recur)))))))
    (subs (apply str (map #(format "%02x" (Byte/toUnsignedInt %)) (.digest digest))) 0 8)))


(def sw-version-file
  "Read back by `service-worker-handler` in core.clj under the same name."
  "sw-version")


(defn uber
  [_]
  (clean nil)

  (println "Copying resources...")
  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir class-dir})

  (println "Stamping the service worker version...")
  (let [version (assets-digest (io/file class-dir "public"))]
    (spit (io/file class-dir sw-version-file) version)
    (println (format "Service worker version: %s" version)))

  (println "Compiling files...")
  (b/compile-clj {:basis      @basis
                  :src-dirs   ["src"]
                  :ns-compile '[sqlite.application-defined-functions core]
                  :class-dir  class-dir})

  (println "Creating uber file...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'core})
  (println (format "Uber file created: %s" uber-file)))


(defn dictionary-import
  [_]
  (println (format "Cleaning %s..." dictionary-class-dir))
  (b/delete {:path dictionary-class-dir})
  (b/delete {:path dictionary-uber-file})

  (println "Compiling dictionary import...")
  (b/compile-clj {:basis      @dictionary-basis
                  :src-dirs   ["tools/dictionary"]
                  :ns-compile '[dictionary.import]
                  :class-dir  dictionary-class-dir})

  (println "Creating dictionary import uber file...")
  (b/uber {:class-dir dictionary-class-dir
           :uber-file dictionary-uber-file
           :basis     @dictionary-basis
           :main      'dictionary.import})
  (println (format "Dictionary import uber file created: %s" dictionary-uber-file)))


(defn run
  [_]
  (let [resources-dir "resources"
        class-path    (str/join ":" [uber-file])] ; Using ':' as classpath separator on Unix/Linux
    (println (format "Running '%s' with resources at '%s'" uber-file resources-dir))
    (let [process (-> (java.lang.ProcessBuilder.
                       ["java" "--class-path" class-path "core"])
                      (.inheritIO)
                      (.start))]
      (.waitFor process))))
