(ns dev.build-identity
  "A shadow-cljs build hook that tells a development build which checkout it
   came from: the short commit, a `+` when that tree had uncommitted changes,
   and the day, month and time it was compiled.

   The value lands on the `build-identity/stamp` goog-define. `:compile-prepare`
   is what makes it a build identity rather than a server identity — `watch`
   runs the stage again on every recompile, and the browser target rewrites the
   module file, which is where CLOSURE_DEFINES lives, on every flush.

   A release build leaves the state untouched: no git process runs, the define
   keeps its empty default, and every reader of it sits under `goog/DEBUG`."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as str])
  (:import
   (java.time LocalDateTime)
   (java.time.format DateTimeFormatter)))


(def ^:private clock
  "Day and month before the clock, the order the Russian interface reads a
   date in."
  (DateTimeFormatter/ofPattern "dd.MM HH:mm"))


(defn- git
  "What a git command printed, trimmed — nil when it failed, which is a
   checkout with no git or no commit yet."
  [& args]
  (let [{:keys [exit out]} (apply shell/sh "git" args)]
    (when (zero? exit)
      (str/trim out))))


(defn ^{:shadow.build/stage :compile-prepare} stamp-the-build
  [build-state]
  (if (= :dev (:shadow.build/mode build-state))
    (assoc-in build-state
     [:compiler-options :closure-defines 'build-identity/stamp]
     (str (or (git "rev-parse" "--short" "HEAD") "no-git")
          (when-not (str/blank? (git "status" "--porcelain")) "+")
          " "
          (.format (LocalDateTime/now) clock)))
    build-state))
