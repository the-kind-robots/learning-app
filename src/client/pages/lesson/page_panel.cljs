(ns pages.lesson.page-panel
  (:require
   [shadow.cljs.modern :as modern]))


;; The page panel: a custom element, `<page-panel>`, under a `<page-strip>`. Its children lie over one another, and
;; the one shown is the last that is not inert. It keeps how far up from its bottom that child reaches in its own
;; `--page-panel-height`, for the page's CSS to paint behind them; while its `page` attribute changes it carries
;; `easing`, under which the CSS lets that height travel. It knows nothing of the app state or of what its children hold.
;;
;; Properties the element keeps: `sizes` (the observer of its children's size) and `settle` (the timer that ends
;; `easing`).


(defn- measure!
  "Puts how far up from the panel's bottom the child shown reaches in `--page-panel-height`."
  [^js panel]
  (when-let [^js shown (->> (js/Array.from (.-children panel))
                            reverse
                            (remove (fn [^js child] (.-inert child)))
                            first)]
    (.setProperty (.-style panel)
                  "--page-panel-height"
                  (str (- (.-bottom (.getBoundingClientRect panel)) (.-top (.getBoundingClientRect shown))) "px"))))


(defn- watch!
  "Measures now and whenever a child changes size."
  [^js panel]
  (let [^js sizes (.-sizes panel)]
    (.disconnect sizes)
    (doseq [child (js/Array.from (.-children panel))]
      (.observe sizes child))
    (measure! panel)))


;; Registered when this namespace loads, once even if it loads again.


(when-not (js/customElements.get "page-panel")
  (modern/defclass PagePanel
    (extends js/HTMLElement)

    (constructor [this]
                 (set! (.-sizes this) (js/ResizeObserver. #(measure! this))))

    Object
    (connectedCallback [this]
                       (watch! this))

    (disconnectedCallback [this]
                          (.disconnect ^js (.-sizes this))
                          (js/clearTimeout (.-settle this)))

    (attributeChangedCallback [this _ _ _]
                              (when (.-isConnected this)
                                (.setAttribute this "easing" "")
                                ;; The render that changed `page` changes the children too, after it: the child
                                ;; shown is known once that render is over.
                                (js/queueMicrotask #(watch! this))
                                (js/clearTimeout (.-settle this))
                                ;; The page's longest transition is 280 ms; a little more lets it end.
                                (set! (.-settle this)
                                      (js/setTimeout #(.removeAttribute this "easing") 380)))))

  (js/Object.defineProperty PagePanel "observedAttributes" #js {:get (fn [] #js ["page"])})
  (js/customElements.define "page-panel" PagePanel))
