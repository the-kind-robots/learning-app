(ns pages.stats.picture
  "The picture «ПОДЕЛИТЬСЯ» shares, laid out as shapes: what is drawn where, in the picture's pixels. Drawing
   them is the sharing port's. Colours are the tokens', which the page resolves in the scheme it shows (#809)."
  (:require
   [pages.stats.presenter :as presenter]))


(def ^:private width 1080)


(def ^:private height 1350)


(def ^:private margin 66)


(def ^:private text-colour "rgb(var(--color-black-text))")


(def ^:private grey-colour "rgb(var(--color-quiet-text))")


(def ^:private soft-fill
  "The opacity of the bars, as the statistics screen fades its own: half by day, more at night."
  "var(--soft-fill-opacity)")


(defn- across
  "`n` places across the picture's width inside its margins, `gap` apart: the left edge and width of each."
  [n gap]
  (let [column (/ (- width (* 2 margin) (* gap (dec n))) n)]
    (for [index (range n)]
      {:width column
       :x     (+ margin (* index (+ column gap)))})))


(defn- text
  [x y size colour align content]
  {:align  align
   :fill   colour
   :shape  :shape/text
   :size   size
   :text   content
   :weight 800
   :x      x
   :y      y})


(defn- rect
  [x y w h radius colour opacity]
  {:fill    colour
   :height  h
   :opacity opacity
   :radius  radius
   :shape   :shape/rect
   :width   w
   :x       x
   :y       y})


(defn picture
  "The picture of the statistics in `state`: `:width`, `:height`, `:file-name` and its `:shapes`, back to front.
   Texts are `[:i18n …]` markup until the caller interpolates them."
  [state]
  (let [{:keys [columns date days lessons shares words]} (presenter/picture-props state)
        columns-bottom 600
        days-bottom    1180]
    {:file-name "sprecha.png"
     :height height
     :shapes
     (concat
      [(rect 0 0 width height 0 "rgb(var(--color-snow))" 1)
       (text margin 130 60 text-colour :align/left [:i18n :shell/word-mark])
       (text (- width margin) 130 36 grey-colour :align/right date)
       (text margin 250 78 text-colour :align/left [:i18n :stats/words {:count words}])]
      (mapcat (fn [{:keys [color] column-words :words column-height :height} {:keys [x] place :width}]
                (let [h (max 66 (* 3.3 column-height))]
                  [(rect x (- columns-bottom h) place h 24 color soft-fill)
                   (text (+ x (/ place 2))
                         (+ (- columns-bottom h) 44)
                         30 text-colour
                         :align/center
                         (str column-words))]))
       columns
       (across 10 12))
      ;; The strip: a part per share, as wide as its share of the words, 9 px apart.
      (let [total  (max 1 words)
            usable (- width (* 2 margin) (* 3 9))
            part   #(* usable (/ (:words %) total))]
        (map (fn [{:keys [color] :as share} x]
               (rect x 630 (part share) 24 12 color 1))
             shares
             (reductions #(+ %1 (part %2) 9) margin shares)))
      (mapcat (fn [{:keys [color name] share-words :words} [x y]]
                [(rect x (- y 30) 30 30 15 color 1)
                 (text (+ x 48) y 48 text-colour :align/left (str share-words))
                 (text (+ x 48) (+ y 46) 32 grey-colour :align/left [:i18n name])])
       shares
       [[margin 740] [540 740] [margin 860] [540 860]])
      [(text margin 980 78 text-colour :align/left [:i18n :stats/lessons {:count lessons}])
       (rect margin days-bottom (- width (* 2 margin)) 6 3 "rgb(var(--color-swan))" 1)]
      ;; A column per day, its lessons over it on a day with any, its date and weekday under the line.
      (mapcat
       (fn [{:keys [day purple? weekday] day-height :height day-lessons :lessons} {:keys [x] place :width}]
         (let [h      (* 1.4 day-height)
               middle (+ x (/ place 2))]
           (cond->
             [(rect x
                    (- days-bottom h)
                    place
                    h
                    12
                    (if purple? "rgb(var(--color-mystic))" "rgb(var(--color-star))")
                    soft-fill)
              (text middle 1224 30 text-colour :align/center (str day))
              (text middle 1258 24 grey-colour :align/center weekday)]
             day-lessons (conj (text middle (- days-bottom h 12) 28 text-colour :align/center (str day-lessons))))))
       days
       (across 14 12))
      [(text (/ width 2) 1320 36 "rgb(var(--color-thallium-dark))" :align/center "sprecha.de")])
     :width width}))
