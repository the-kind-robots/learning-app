(ns adapters.sharing
  "A picture drawn on a canvas and handed out: to the system's share sheet, or downloaded where there is none."
  (:require
   [lambdaisland.glogi :as log]))


(defn ^:async share-picture!
  "Draws `picture`, `{:width :height :shapes :file-name}`, as a PNG and offers it to the share sheet, or downloads
   it where the browser cannot share files. Resolves when it is handed out; a share the learner cancels is no error."
  [{:keys [file-name height shapes width]}]
  (await (.-ready js/document.fonts))
  (let [^js canvas  (js/document.createElement "canvas")
        ^js context (.getContext canvas "2d")
        ;; A colour as the page computes it, `color-mix()` included, which the canvas may not parse itself.
        ^js probe   (js/document.createElement "span")]
    (set! (.-width canvas) width)
    (set! (.-height canvas) height)
    (js/document.body.append probe)
    (doseq [{:keys [align fill opacity radius shape size text weight x y] shape-height :height shape-width :width}
            shapes]
      (set! (.. probe -style -color) fill)
      (set! (.-fillStyle context) (.-color (js/getComputedStyle probe)))
      (case shape
        :shape/rect (do
                      (set! (.-globalAlpha context) opacity)
                      (.beginPath context)
                      (.roundRect context x y shape-width shape-height radius)
                      (.fill context))
        :shape/text (do
                      (set! (.-globalAlpha context) 1)
                      (set! (.-font context) (str weight " " size "px Nunito, sans-serif"))
                      (set! (.-textAlign context) (name align))
                      (.fillText context text x y))))
    (.remove probe)
    (let [blob     (await (js/Promise. (fn [resolve _] (.toBlob canvas resolve "image/png"))))
          ^js file (js/File. #js [blob] file-name #js {:type "image/png"})
          files    #js {:files #js [file]}]
      (if (and (.-canShare js/navigator) (js-invoke js/navigator "canShare" files))
        (try
          (await (js-invoke js/navigator "share" files))
          (catch :default ^js err
            (when-not (= "AbortError" (.-name err))
              (log/warn :sharing/share-failed {:error (ex-message err)}))))
        (let [url      (js/URL.createObjectURL file)
              ^js link (js/document.createElement "a")]
          (set! (.-href link) url)
          (set! (.-download link) file-name)
          (.click link)
          (js/setTimeout #(js/URL.revokeObjectURL url) 1000))))))
