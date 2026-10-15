(ns pages.lesson.answer-field
  (:require
   [clojure.string :as str]
   [replicant.string :as rs]
   [shadow.cljs.modern :as modern]
   [shadow.resource :as rc]))


;; The answer field: a custom element, `<answer-field>`, used like a textarea
;; with a twist. It takes a textarea's attributes (`name`, `placeholder`,
;; `maxlength`, `aria-label`, ...), has a `value`, takes part in its form and
;; fires `input` when the value changes, so the page treats it as it treated a
;; textarea. The twist is a light bulb, shown when it has a `correct-answer`: a
;; one-way checkbox that, once on, swaps the textarea for tiles of the answer's
;; words, a bank above the field and the placed tiles in it. The value is then
;; the placed words, otherwise the typed text. It knows nothing of the app state,
;; Nexus or the page's CSS, and no UI text: `bank-label`, `tray-label` and
;; `hint-label` name its parts. It uses Replicant's string renderer for one
;; thing, writing its own skeleton as HTML once; the tiles are made and moved by
;; hand.
;;
;; What the markup and CSS (`answer_field.css`) can do they do: the bulb is the
;; checkbox itself, and which of textarea, tray and bank is shown is the
;; checkbox's state, read by sibling selectors. The script only builds the
;; tiles, moves them (tap, drag, slide) and keeps the value.
;;
;; Looks come from outside: font and colour by inheritance, the field's box and
;; the tiles' gaps by custom properties (`--answer-field-background`, `-border`,
;; `-radius`, `-padding`, `-min-height`, `-gap`, `-hint-color`,
;; `-caret-color`), and the one part, `tile`, for the tile's appearance.
;;
;; Properties the element keeps: `internals` (its form side), `drag` (the
;; AbortController of the drag in progress) and `dragged` (set when a drag
;; ends, for the click that follows it: that is no tap).


(def ^:private skeleton
  (rs/render
   (list
    [:input#hint
     {:type "checkbox"}]
    [:div#bank
     {:role "group"}]
    [:div#field
     [:textarea
      {:autofocus true}]
     [:div#tray
      {:role "group"}]])))


(def ^:private stylesheet
  (delay (doto (js/CSSStyleSheet.)
           (.replaceSync (rc/inline "./answer_field.css")))))


(def ^:private reduced-motion
  (delay (js/matchMedia "(prefers-reduced-motion: reduce)")))


(def ^:private slide-ms
  180)


(def ^:private drag-threshold-px
  "A pointer that travelled no farther than this was a tap."
  10)


(def ^:private edge-punctuation
  (js/RegExp. "^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$" "gu"))


(def ^:private forwarded
  "The attributes the field hands to its textarea."
  ["aria-label" "autocapitalize" "autocomplete" "autocorrect" "enterkeyhint"
   "maxlength" "placeholder" "spellcheck"])


(def ^:private labelled
  "The attributes that name a part of the field, and the part."
  {"bank-label" "#bank" "hint-label" "#hint" "tray-label" "#tray"})


(defn- words-of
  "The words of `sentence`, in order, with their case and without the
   punctuation at their edges."
  [sentence]
  (->> (str/split (str/trim (or sentence "")) #"\s+")
       (map #(str/replace % edge-punctuation ""))
       (remove str/blank?)
       vec))


(defn- shuffled-words
  "The `words` in a shuffled order that never spells the sentence itself,
   unless every order does."
  [words]
  (if (< (count (distinct words)) 2)
    words
    (loop [shuffled (shuffle words)]
      (if (= words shuffled)
        (recur (shuffle words))
        shuffled))))


(defn- inside
  [^js el selector]
  (.querySelector (.-shadowRoot el) selector))


(defn- tiles-of
  [^js el]
  (array-seq (.querySelectorAll (.-shadowRoot el) "button")))


(defn- value-of
  "The placed words once the bulb is on, the typed text before."
  [^js el]
  (if (.-checked (inside el "#hint"))
    (str/join " " (map #(.-textContent ^js %) (array-seq (.-children (inside el "#tray")))))
    (.-value (inside el "textarea"))))


(defn- publish!
  "Hands the form the value."
  [^js el]
  (.setFormValue (.-internals el) (value-of el)))


(defn- placed!
  "The placed tiles changed: the value did, and the field says so."
  [^js el]
  (publish! el)
  (.dispatchEvent el (js/Event. "input" #js {:bubbles true :composed true})))


;; Tiles slide to their new places instead of jumping (FLIP): the places are
;; read before a node moves, and each tile is animated from there once it has.


(defn- rects-of
  "The places of `tiles`, [{:tile :left :top :width :height}]."
  [tiles]
  (mapv (fn [^js tile]
          (let [rect (.getBoundingClientRect tile)]
            {:tile   tile
             :left   (.-left rect)
             :top    (.-top rect)
             :width  (.-width rect)
             :height (.-height rect)}))
        tiles))


(defn- slide!
  "Animates the tiles from the places `before`, [{:tile :left :top}], to where
   they are now: all read first, then all written."
  [before]
  (let [now (rects-of (map :tile before))]
    (doseq [[was is] (map vector before now)
            :let     [dx (- (:left was) (:left is))
                      dy (- (:top was) (:top is))]
            :when    (or (> (js/Math.abs dx) 0.5) (> (js/Math.abs dy) 0.5))]
      (.animate ^js (:tile is)
                #js [#js {:transform (str "translate(" dx "px, " dy "px)") :zIndex 1}
                     #js {:transform "none" :zIndex 1}]
                #js {:duration slide-ms :easing "cubic-bezier(.2, .8, .2, 1)"}))))


(defn- moving
  "Moves what `move!` moves, sliding the tiles unless motion is reduced."
  [^js el move!]
  (let [before (when-not (.-matches @reduced-motion)
                 (rects-of (tiles-of el)))]
    (move!)
    (placed! el)
    (when before
      (slide! before))))


(defn- tapped!
  "A bank tile goes to the end of the tray, a placed one back to the bank where
   its rank puts it. The tile keeps the focus it had."
  [^js el ^js tile]
  (let [tray (inside el "#tray")
        bank (inside el "#bank")]
    (moving el
            #(if (identical? tray (.-parentNode tile))
               (.insertBefore bank
                              tile
                              (first (filter (fn [^js other] (> (.-rank other) (.-rank tile)))
                                             (array-seq (.-children bank)))))
               (.append tray tile)))
    (.focus tile)))


;; The caret of a drag: one bar after the tile the dragged one would follow, or
;; before the first tile. The target is [tile side], side :before or :after.


(defn- caret-target
  "Where a tile dropped at (`x`, `y`) would land among `others`, as
   [tile side], or nil when there is no other tile."
  [others x y]
  (let [centre-x (fn [{:keys [left width]}] (+ left (/ width 2)))
        distance (fn [{:keys [top height] :as other}]
                   (js/Math.hypot (- x (centre-x other)) (- y (+ top (/ height 2)))))]
    (when (seq others)
      (let [nearest (apply min-key distance others)
            at      (.indexOf others nearest)]
        (cond
          (>= x (centre-x nearest)) [(:tile nearest) :after]
          (pos? at) [(:tile (get others (dec at))) :after]
          :else [(:tile nearest) :before])))))


(defn- show-caret!
  "Moves the caret from the target `from` to the target `to`."
  [from to]
  (when from
    (.removeAttribute ^js (first from) "data-caret"))
  (when to
    (.setAttribute ^js (first to) "data-caret" (name (second to)))))


(defn- drag!
  "Follows the primary pointer that went down on a placed tile, which the tile
   captures: past the threshold the tile is lifted and follows it, with a caret
   where it would land; on release the tray is reordered."
  [^js el ^js tile ^js event]
  (let [tray       (inside el "#tray")
        start-x    (.-clientX event)
        start-y    (.-clientY event)
        state      (volatile! nil)
        controller (js/AbortController.)
        listen     (fn [type f]
                     (.addEventListener tile type f #js {:signal (.-signal controller)}))
        settle!    (fn []
                     (.abort controller)
                     (set! (.-drag el) nil)
                     (show-caret! (:target @state) nil)
                     (.removeAttribute tile "data-lifted")
                     (set! (.. tile -style -transform) ""))]
    (set! (.-drag el) controller)
    (.setPointerCapture tile (.-pointerId event))
    (listen "pointermove"
            (fn [^js move]
              (let [dx (- (.-clientX move) start-x)
                    dy (- (.-clientY move) start-y)]
                (when (and (nil? @state) (> (js/Math.hypot dx dy) drag-threshold-px))
                  (vreset! state {:others (rects-of (remove #(identical? tile %) (array-seq (.-children tray))))})
                  (.setAttribute tile "data-lifted" ""))
                (when @state
                  (set! (.. tile -style -transform) (str "translate(" dx "px, " dy "px)"))
                  (let [target (caret-target (:others @state) (.-clientX move) (.-clientY move))]
                    (when (not= target (:target @state))
                      (show-caret! (:target @state) target))
                    (vswap! state assoc :target target))))))
    (listen "pointerup"
            (fn [_]
              (if @state
                (do
                  (set! (.-dragged el) true)
                  (moving el
                          #(let [[^js other side] (:target @state)]
                             (settle!)
                             (when other
                               (.insertBefore tray tile (if (= :after side) (.-nextElementSibling other) other))))))
                (settle!))))
    (listen "lostpointercapture" (fn [_] (settle!)))))


;; The element.


(defn- listen!
  [^js el]
  (let [root (.-shadowRoot el)
        tile (fn [^js event] (.closest (.-target event) "button"))]
    (.addEventListener root
                       "click"
                       (fn [^js event]
                         (when-let [^js tile (tile event)]
                           (if (.-dragged el)
                             (set! (.-dragged el) false)
                             (tapped! el tile)))))
    (.addEventListener root
                       "pointerdown"
                       (fn [^js event]
                         (set! (.-dragged el) false)
                         (when-let [^js tile (tile event)]
                           (when (and (identical? (inside el "#tray") (.-parentNode tile))
                                      (.-isPrimary event)
                                      (zero? (.-button event))
                                      (nil? (.-drag el)))
                             (drag! el tile event)))))
    (.addEventListener root "input" (fn [_] (publish! el)))
    (.addEventListener root "change" (fn [_] (publish! el)))))


(defn- tiles-for!
  "Puts a button for each word of `answer` in the bank, shuffled, and nothing
   in the tray. A tile's rank is its place in the bank at the start."
  [^js el answer]
  (.replaceChildren (inside el "#bank"))
  (.replaceChildren (inside el "#tray"))
  (doseq [[rank word] (map-indexed vector (shuffled-words (words-of answer)))]
    (.append (inside el "#bank")
             (doto (js/document.createElement "button")
               (.setAttribute "type" "button")
               (.setAttribute "part" "tile")
               (aset "rank" rank)
               (.append word)))))


;; Registered when this namespace loads, once even if it loads again.


(when-not (js/customElements.get "answer-field")
  (modern/defclass AnswerField
    (extends js/HTMLElement)

    (constructor [this]
                 (let [root (.attachShadow this #js {:mode "open" :delegatesFocus true})]
                   (set! (.-internals this) (.attachInternals this))
                   (set! (.-innerHTML root) skeleton)
                   (set! (.-adoptedStyleSheets root) #js [@stylesheet])
                   (listen! this)))

    Object
    (attributeChangedCallback [this attribute _ value]
                              (if (= "correct-answer" attribute)
                                (tiles-for! this value)
                                (let [[selector name] (if-let [part (labelled attribute)]
                                                        [part "aria-label"]
                                                        ["textarea" attribute])
                                      target (inside this selector)]
                                  (if value
                                    (.setAttribute target name value)
                                    (.removeAttribute target name))))))

  (js/Object.defineProperty AnswerField "formAssociated" #js {:value true})
  (js/Object.defineProperty AnswerField
                            "observedAttributes"
                            #js {:get (fn [] (into-array (concat forwarded (keys labelled) ["correct-answer"])))})
  (js/Object.defineProperty (.-prototype AnswerField)
                            "value"
                            #js {:get (fn [] (this-as ^js this (value-of this)))
                                 :set (fn [value]
                                        (this-as ^js this
                                                 (set! (.-value (inside this "textarea")) value)
                                                 (publish! this)))})
  (js/Object.defineProperty (.-prototype AnswerField)
                            "form"
                            #js {:get (fn [] (this-as ^js this (.-form (.-internals this))))})
  (js/customElements.define "answer-field" AnswerField))
