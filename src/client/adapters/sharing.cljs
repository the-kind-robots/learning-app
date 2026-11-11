(ns adapters.sharing
  "A picture, SVG hiccup, painted as a PNG and handed out: to the system's share sheet, or downloaded where there is
   none."
  (:require
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [replicant.string :as rs]))


(defn- css
  "The CSS of `sheets` as the page applies it now: a sheet whose media does not match is left out, and so is one the
   page may not read (another origin's); a sheet one imports stands in its layer."
  [sheets]
  (->> sheets
       (filter #(.-matches (js/matchMedia (.. ^js % -media -mediaText))))
       (keep (fn [^js sheet]
               (try
                 (.-cssRules sheet)
                 (catch :default _
                   nil))))
       (mapcat seq)
       (map (fn [^js rule]
              (if (instance? js/CSSImportRule rule)
                (str "@layer " (.-layerName rule) " {" (css [(.-styleSheet rule)]) "}")
                (.-cssText rule))))
       (str/join "\n")))


(defn ^:async share-picture!
  "Paints `svg`, SVG hiccup in the page's CSS, three times its size, as a PNG named `file-name` and offers it to the
   share sheet, or downloads it where the browser cannot share files. Resolves when it is handed out; a share the
   learner cancels is no error."
  [file-name svg]
  ;; An SVG painted as an image reaches nothing outside itself, so the page's CSS goes into it, as CDATA, the font
  ;; files as `data:` URLs; and the SVG goes to the image as a `data:` URL too, since from a `blob:` URL a
  ;; `<foreignObject>` taints the canvas, in WebKit and Chrome alike.
  (let [page-css  (css (.-styleSheets js/document))
        fonts     (->> (re-seq #"url\(\"?([^\")]+\.woff2)\"?\)" page-css)
                       (map second)
                       distinct)
        data-urls (->> fonts
                       (map (fn ^:async data-url [font]
                              (let [^js response (await (js/fetch font))
                                    blob         (await (.blob response))
                                    ^js reader   (js/FileReader.)]
                                (await (js/Promise. (fn [resolve _]
                                                      (set! (.-onload reader) resolve)
                                                      (.readAsDataURL reader blob))))
                                (.-result reader))))
                       into-array
                       js/Promise.all
                       await)
        markup    (rs/render (conj svg
                                   [:style
                                    {:innerHTML (str "<![CDATA["
                                                     (reduce-kv str/replace page-css (zipmap fonts data-urls))
                                                     "]]>")}]))
        ^js image (js/Image.)]
    (set! (.-src image) (str "data:image/svg+xml;charset=utf-8," (js/encodeURIComponent markup)))
    (await (.decode image))
    ;; Three of the PNG's pixels to the picture's one, as on a phone's screen.
    (let [^js canvas  (js/OffscreenCanvas. (* 3 (.-naturalWidth image)) (* 3 (.-naturalHeight image)))
          ^js context (.getContext canvas "2d")]
      (.drawImage context image 0 0 (.-width canvas) (.-height canvas))
      (let [^js file (js/File. #js [(await (.convertToBlob canvas))] file-name #js {:type "image/png"})
            files    #js {:files #js [file]}]
        (if (and (.-canShare js/navigator) (js-invoke js/navigator "canShare" files))
          (try
            (await (js-invoke js/navigator "share" files))
            (catch :default ^js err
              (when-not (= "AbortError" (.-name err))
                (log/warn :sharing/share-failed {:error (ex-message err)}))))
          (let [file-url (js/URL.createObjectURL file)
                ^js link (js/document.createElement "a")]
            (set! (.-href link) file-url)
            (set! (.-download link) file-name)
            (.click link)
            (js/setTimeout #(js/URL.revokeObjectURL file-url) 1000)))))))
