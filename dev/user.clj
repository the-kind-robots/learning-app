(ns user
  (:require
   [debux.core]
   [sqlite.application-defined-functions]))


;; `i18n` inlines the language files as it compiles, and a class newer than its source is loaded in its place:
;; a language file added or edited since would not show. Its classes go before each start, so it compiles anew.
(doseq [^java.io.File file (.listFiles (java.io.File. "classes"))
        :when (re-matches #"i18n(\$.*|__init)\.class" (.getName file))]
  (.delete file))


;; Improving Development Startup Time
;; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true ;; compile during load
          *compile-path*  "classes"]
  (require 'user :reload-all))


;; reload this and all transitively loaded namespaces


;; Using `require-resolve` instead of `:require` to avoid compile class pollution in the code above.


(def start-server
  (requiring-resolve 'core/-main))


(def stop-server
  (requiring-resolve 'core/stop-server!))


(def reload-deps
  (requiring-resolve 'clojure.repl.deps/sync-deps))


(comment
  (start-server)
  (stop-server)
  (reload-deps)
)
