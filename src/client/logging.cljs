;; Client-side logging configuration.
(ns logging
  (:require
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]))


(def ^:private debug-mode?
  ^boolean js/goog.DEBUG)


(defonce initialized?
  (do
    (when debug-mode?
      (glogi-console/install!))
    (log/set-levels {:glogi/root (if debug-mode? :debug :off)})
    true))
