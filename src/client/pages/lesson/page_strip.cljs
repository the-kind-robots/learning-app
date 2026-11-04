(ns pages.lesson.page-strip
  (:require
   [shadow.cljs.modern :as modern]))


;; The page strip: a custom element, `<page-strip>`, whose children are pages a
;; strip's width each, scrolled sideways and snapped by the page's CSS. Its
;; `page` attribute is the page to show: the strip scrolls there, at once when
;; the page is new, smoothly otherwise. A page the learner scrolls to is
;; reported as `pagechange`, `{:detail {:page n}}`, once the page is more than
;; half in view; the attribute that follows it moves nothing. It knows nothing
;; of the app state, Nexus or what the pages hold.
;;
;; Properties the element keeps: `pages` (the pages it last scrolled among),
;; `reported` (the page it last reported or reached) and `target` (the page of
;; its own smooth scroll in progress, nil when none; the pages it passes on the
;; way are not reported).


(defn- show!
  "Scrolls `strip` to its `page`, after the render that set it has put the pages in place."
  [^js strip]
  (let [page  (js/Number (.getAttribute strip "page"))
        pages (.-childElementCount strip)
        new?  (not= pages (.-pages strip))]
    (set! (.-pages strip) pages)
    (set! (.-reported strip) page)
    (set! (.-target strip) (when-not new? page))
    (.scrollTo strip
               #js {:behavior (if new? "instant" "smooth")
                    :left     (* page (.-clientWidth strip))})))


(defn- scrolled!
  [^js strip]
  ;; The page more than half in view.
  (let [page (js/Math.round (/ (.-scrollLeft strip) (.-clientWidth strip)))]
    (cond
      (some? (.-target strip))
      (when (= page (.-target strip))
        (set! (.-target strip) nil))

      (not= page (.-reported strip))
      (do (set! (.-reported strip) page)
          (.dispatchEvent strip (js/CustomEvent. "pagechange" #js {:bubbles true :detail #js {:page page}}))))))


;; Registered when this namespace loads, once even if it loads again.


(when-not (js/customElements.get "page-strip")
  (modern/defclass PageStrip
    (extends js/HTMLElement)

    (constructor [this]
                 (.addEventListener this "scroll" #(scrolled! this) #js {:passive true})
                 ;; A finger or a wheel takes over from a smooth scroll in progress.
                 (.addEventListener this "pointerdown" #(set! (.-target this) nil) #js {:passive true})
                 (.addEventListener this "wheel" #(set! (.-target this) nil) #js {:passive true}))

    Object
    (attributeChangedCallback [this _ _ value]
                              (when (not= (js/Number value) (.-reported this))
                                (js/queueMicrotask #(show! this)))))

  (js/Object.defineProperty PageStrip "observedAttributes" #js {:get (fn [] #js ["page"])})
  (js/customElements.define "page-strip" PageStrip))
