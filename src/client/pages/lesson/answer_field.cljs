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
;; button, pressed once by tap or Alt+Enter, that swaps the textarea for tiles
;; of the answer's words, a bank above the field and the placed tiles in it.
;; Unless `bulb?` is set, it shows the tiles from the start instead. The
;; value is then the placed words, otherwise the typed text. It knows nothing of
;; the app state, Nexus or the page's CSS, and no UI text: `bank-label`,
;; `tray-label` and `hint-label` name its parts. It uses Replicant's string
;; renderer for one thing, writing its own skeleton as HTML once; the tiles are
;; made and moved by hand.
;;
;; What the markup and CSS (`answer_field.css`) can do they do: which of
;; textarea, tray and bank is shown is the element's custom state, `tiles`,
;; read by `:state(tiles)`. The script only sets that state, builds the tiles,
;; moves them (tap, drag, slide) and keeps the value.
;;
;; Looks come from outside: font and colour by inheritance, the field's box and
;; the tiles' gaps by custom properties (`--answer-field-background`, `-border`,
;; `-radius`, `-padding`, `-min-height`, `-gap`, `-hint-color`,
;; `-caret-color`), and the one part, `tile`, for the tile's appearance.
;;
;; Tiles are placed by tap, by drag (a bank tile too) and by typing: letters
;; typed on a keyboard make a prefix, shown as heavier leading letters on the
;; bank tiles it fits (`--answer-field-match-weight`), and a bank tile moves to
;; the tray once the prefix fits one distinct bank word. A word is typed as if
;; it had no umlauts: «uber» fits «über», «gross» fits «groß».
;;
;; Properties the element keeps: `internals` (its form side), `drag` (the
;; AbortController of the drag in progress), `dragged` (set when a drag
;; ends, for the click that follows it: that is no tap) and `pending` (the
;; letters typed and not yet placed).


(def ^:private skeleton
  (rs/render
   (list
    [:div#bank
     {:role "group"}]
    [:div#field
     [:textarea]
     [:div#tray
      {:autofocus true :role "group" :tabindex "-1"}]]
    [:button#hint
     {:type "button"}
     [:svg
      {:aria-hidden    "true"
       :fill           "none"
       :stroke         "currentColor"
       :stroke-linecap "round"
       :stroke-linejoin "round"
       :stroke-width   "2.2"
       :viewBox        "0 0 24 24"}
      [:path {:d "M9 15.5a5.5 5.5 0 1 1 6 0V18H9z"}]
      [:path {:d "M10 21h4"}]]])))


(def ^:private stylesheet
  (delay (doto (js/CSSStyleSheet.)
           (.replaceSync (rc/inline "./answer_field.css")))))


(def ^:private reduced-motion
  (delay (js/matchMedia "(prefers-reduced-motion: reduce)")))


(def ^:private bulb?
  "Whether a field with a `correct-answer` starts as a textarea with the light
   bulb; otherwise it starts with the tiles."
  false)


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


(defn- typed-form
  "`text` as typed on a keyboard without umlauts: in lower case, with ä, ö, ü as
   a, o, u and ß as ss."
  [text]
  (str/replace (str/lower-case text) #"[äöüß]" {"ä" "a" "ö" "o" "ü" "u" "ß" "ss"}))


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
  (array-seq (.querySelectorAll (.-shadowRoot el) "[part=tile]")))


(defn- tiles?
  [^js el]
  (.has (.. el -internals -states) "tiles"))


(defn- value-of
  "The placed words once the bulb is pressed, the typed text before."
  [^js el]
  (if (tiles? el)
    (str/join " " (map #(.-textContent ^js %) (array-seq (.-children (inside el "#tray")))))
    (.-value (inside el "textarea"))))


(defn- publish!
  "Hands the form the value."
  [^js el]
  (.setFormValue (.-internals el) (value-of el)))


(defn- typed!
  "Sets the pending prefix to `prefix` and shows it: the leading letters it
   spells go heavier on each bank tile whose word starts with it. A letter
   typed as two, ß as ss, goes heavier once both are typed."
  [^js el prefix]
  (set! (.-pending el) prefix)
  (let [bank (inside el "#bank")]
    (doseq [^js tile (tiles-of el)
            :let     [^js span (.-firstElementChild tile)
                      word     (.getAttribute span "data-word")
                      matched  (if (and (seq prefix)
                                        (identical? bank (.-parentNode tile))
                                        (str/starts-with? (typed-form word) prefix))
                                 (->> (map (comp count typed-form) word)
                                      (reductions +)
                                      (take-while #(<= % (count prefix)))
                                      count)
                                 0)]]
      (.replaceChildren span)
      (when (pos? matched)
        (.append span
                 (doto (js/document.createElement "b")
                   (.append (subs word 0 matched)))))
      (.append span (subs word matched)))))


(defn- placed!
  "The placed tiles changed: the value did, and the field says so. Whatever was
   typed is stale."
  [^js el]
  (typed! el "")
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


(defn- over?
  "Whether (`x`, `y`) is inside `rect`."
  [^js rect x y]
  (and (<= (.-left rect) x (.-right rect))
       (<= (.-top rect) y (.-bottom rect))))


(defn- drag!
  "Follows the primary pointer that went down on a tile, which the tile
   captures: past the threshold the tile is lifted and follows it, with a caret
   where it would land; on release a placed tile is reordered, and a bank tile
   dropped on the field goes into the tray. The tile keeps the focus, so typing
   goes on placing tiles."
  [^js el ^js tile ^js event]
  (let [tray       (inside el "#tray")
        banked?    (not (identical? tray (.-parentNode tile)))
        field-rect (.getBoundingClientRect (inside el "#field"))
        landing?   (fn [x y] (or (not banked?) (over? field-rect x y)))
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
                  (let [target (when (landing? (.-clientX move) (.-clientY move))
                                 (caret-target (:others @state) (.-clientX move) (.-clientY move)))]
                    (when (not= target (:target @state))
                      (show-caret! (:target @state) target))
                    (vswap! state assoc :target target))))))
    (listen "pointerup"
            (fn [^js up]
              (if @state
                (let [dropped? (landing? (.-clientX up) (.-clientY up))]
                  (set! (.-dragged el) true)
                  (moving el
                          #(let [[^js other side] (:target @state)]
                             (settle!)
                             (cond
                               other (.insertBefore tray tile (if (= :after side) (.-nextElementSibling other) other))
                               (and banked? dropped?) (.append tray tile))))
                  (.focus tile))
                (settle!))))
    (listen "lostpointercapture" (fn [_] (settle!)))))


;; Typing places tiles. The prefix is compared in its typed form.


(defn- typed-key!
  "A key went down while tiles are shown. A letter joins the prefix, or places
   the one bank word it leaves; Space or Enter places the word the prefix
   identifies, the only one it fits or the one it spells whole, and else does
   nothing; Backspace erases the prefix's last letter or returns the last placed
   tile. Of tiles with the same word the first in the bank is placed. Returns
   whether the key was taken."
  [^js el key]
  (let [prefix  (or (.-pending el) "")
        tray    (inside el "#tray")
        bank    (array-seq (.-children (inside el "#bank")))
        word-of (fn [^js tile] (typed-form (.-textContent tile)))
        place!  (fn [^js tile]
                  (tapped! el tile)
                  (.focus tray))
        tile-of (fn [word] (first (filter #(= word (word-of %)) bank)))
        fitting (fn [typed] (distinct (filter #(str/starts-with? % typed) (map word-of bank))))]
    (cond
      (= "Backspace" key)
      (do (if (seq prefix)
            (typed! el (subs prefix 0 (dec (count prefix))))
            (when-let [last-placed (.-lastElementChild tray)]
              (place! last-placed)))
          true)

      (and (contains? #{" " "Enter"} key) (seq prefix))
      (let [words (fitting prefix)]
        (when-let [word (if (= 1 (count words))
                          (first words)
                          (some #{prefix} words))]
          (place! (tile-of word)))
        true)

      (and (= 1 (count key)) (not (str/blank? key)))
      (let [candidate (str prefix (typed-form key))
            words     (fitting candidate)]
        (case (count words)
          0 nil
          1 (place! (tile-of (first words)))
          (typed! el candidate))
        true))))


;; The element.


(defn- listen!
  [^js el]
  (let [root (.-shadowRoot el)
        tile (fn [^js event] (.closest (.-target event) "[part=tile]"))
        hint (fn []
               (.add (.. el -internals -states) "tiles")
               (publish! el)
               (.focus (inside el "#tray")))]
    (.addEventListener root
                       "click"
                       (fn [^js event]
                         (if-let [^js tile (tile event)]
                           (if (.-dragged el)
                             (set! (.-dragged el) false)
                             (tapped! el tile))
                           (when (.closest (.-target event) "#hint")
                             (hint)))))
    (.addEventListener root
                       "pointerdown"
                       (fn [^js event]
                         (set! (.-dragged el) false)
                         (when-let [^js tile (tile event)]
                           (when (and (.-isPrimary event)
                                      (zero? (.-button event))
                                      (nil? (.-drag el)))
                             (drag! el tile event)))))
    (.addEventListener root
                       "keydown"
                       (fn [^js event]
                         (cond
                           (and (not (tiles? el))
                                (.hasAttribute el "correct-answer")
                                (.-altKey event)
                                (= "Enter" (.-key event)))
                           (do (.preventDefault event)
                               (hint))

                           (and (tiles? el)
                                (not (.-isComposing event))
                                (not (or (.-ctrlKey event) (.-metaKey event) (.-altKey event))))
                           (when (typed-key! el (.-key event))
                             (.preventDefault event)))))
    (.addEventListener root "input" (fn [_] (publish! el)))))


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
               (.append (doto (js/document.createElement "span")
                          (.setAttribute "data-word" word)
                          (.append word)))))))


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
                              (case attribute
                                "correct-answer"
                                (do (tiles-for! this value)
                                    (typed! this "")
                                    (when (and value (not bulb?))
                                      (.add (.. this -internals -states) "tiles")
                                      (publish! this)))

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
                            #js {:get (fn []
                                        (into-array (concat forwarded (keys labelled) ["correct-answer"])))})
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
