(ns splash
  "The splash the server renders into the page before any script runs. It
   follows the system colour scheme with CSS alone (#413): the light one is
   the ü of nine leaf-coloured dots pulsing along the stroke, the dark one is
   the same ü as fireflies, with stray fireflies passing behind foliage that
   is drawn in the ground colour and never shows as a shape."
  (:require
   [garden.core :as garden]
   [garden.stylesheet :as stylesheet]))


(set! *warn-on-reflection* true)


(def ^:private style
  "The splash's own stylesheet. It paints before the app's styles load, so it
   carries the colours of both schemes itself."
  (garden/css
   {:pretty-print? false}
   [:.splash
    {:align-items     "center"
     :background      "#fff"
     :color           "#222"
     :display         "flex"
     :flex-direction  "column"
     :font-family     "Nunito,system-ui,sans-serif"
     :height          "100dvh"
     :justify-content "center"
     :margin          0
     :overflow        "hidden"
     :position        "relative"}]
   [:.splash-title
    {:font-size     "2rem"
     :font-weight   700
     :margin-bottom "1.5rem"
     :position      "relative"}]
   [:.splash-sub
    {:color      "#666"
     :font-size  ".95rem"
     :margin-top "2rem"
     :position   "relative"}]
   [:.splash
    [:circle
     :path
     :g
     {:transform-box    "fill-box"
      :transform-origin "center"}]]
   [:.splash-face
    {:overflow "visible"
     :position "relative"}]
   [:.splash-night
    :.splash-halo
    {:display "none"}]
   [:.splash-wobble {:animation "splash-wobble var(--wobble-duration) ease-in-out var(--wobble-delay) infinite"}]
   [:.dot
    {:animation "pulse 1.4s ease-in-out var(--pulse-delay) infinite alternate"
     :fill      "var(--leaf)"}]
   [:.sparkle
    {:animation "flash 5s ease-out infinite"
     :fill      "hsl(280 62% 60%)"
     :opacity   0}]
   (stylesheet/at-keyframes "pulse"
                            [:from {:opacity 0.45 :transform "scale(.82)"}]
                            [:to {:opacity 1 :transform "scale(1.08)"}])
   (stylesheet/at-keyframes "splash-wobble"
                            ["0%,100%" {:transform "translate(0,0)"}]
                            ["25%" {:transform "translate(var(--ax),var(--ay))"}]
                            ["50%" {:transform "translate(var(--bx),var(--by))"}]
                            ["75%" {:transform "translate(var(--ay),var(--ax))"}])
   (stylesheet/at-keyframes "flash"
                            ["0%" {:opacity 0 :transform "scale(.4)"}]
                            ["7%" {:opacity 1 :transform "scale(1.4)"}]
                            ["20%" {:opacity 0.85 :transform "scale(1)"}]
                            ["45%" {:opacity 0.3 :transform "scale(.8)"}]
                            ["100%" {:opacity 0 :transform "scale(.5)"}])
   (stylesheet/at-keyframes "splash-flicker"
                            ["0%,100%" {:opacity 0.3 :transform "scale(.85)"}]
                            ["35%" {:opacity 0.45 :transform "scale(.9)"}]
                            ["45%" {:opacity 1 :transform "scale(1.12)"}]
                            ["60%" {:opacity 0.55 :transform "scale(.95)"}])
   (stylesheet/at-keyframes "splash-glow"
                            ["0%,100%" {:opacity 0.08 :transform "scale(.7)"}]
                            ["35%" {:opacity 0.15 :transform "scale(.8)"}]
                            ["45%" {:opacity 0.75 :transform "scale(1.15)"}]
                            ["60%" {:opacity 0.25 :transform "scale(.9)"}])
   (stylesheet/at-keyframes "splash-stray"
                            ["0%,100%" {:transform "translate(0,0)"}]
                            ["50%" {:transform "translate(var(--dx),var(--dy))"}])
   (stylesheet/at-keyframes "splash-sway"
                            ["0%,100%" {:transform "rotate(-1.2deg)"}]
                            ["50%" {:transform "rotate(1.2deg)"}])
   (stylesheet/at-media
    {:prefers-color-scheme "dark"}
    [:.splash
     {:background "#150f1c"
      :color      "#f3ecf8"}]
    [:.splash-sub {:color "#ab9eb8"}]
    [:.splash-night
     {:display  "block"
      :height   "100%"
      :inset    0
      :position "absolute"
      :width    "100%"}]
    [:.splash-halo
     {:animation "splash-glow var(--flicker-duration) ease-in-out var(--flicker-delay) infinite"
      :display   "inline"
      :opacity   0.15}]
    [:.dot {:fill "var(--firefly)"}]
    [:.dot
     :.splash-fly
     {:animation "splash-flicker var(--flicker-duration) ease-in-out var(--flicker-delay) infinite"}]
    [:.sparkle {:fill "hsl(285 80% 76%)"}]
    [:.splash-stray {:animation "splash-stray var(--stray-duration) ease-in-out var(--stray-delay) infinite"}]
    [:.splash
     [:.splash-leaf
      {:animation        "splash-sway var(--sway-duration) ease-in-out var(--sway-delay) infinite"
       :transform-origin "0 50%"}]])
   (stylesheet/at-media {:prefers-reduced-motion "reduce"}
                        [:.splash
                         [:*
                          {:animation "none !important"}]]
                        [:.dot
                         :.splash-fly
                         {:opacity 1}]
                        [:.splash-halo {:opacity 0.3}])))


(def ^:private dots
  "The ü in the order of its stroke: down the left side, along the bottom, up
   the right side, then the umlaut. The pulse runs in that order in the light
   scheme; the fireflies of the dark one flicker each on its own clock. Every
   dot wobbles on its own orbit, and the radii are slightly uneven.
   `:wobble` is [ax ay bx by duration delay], `:flicker` [duration delay]."
  [{:cx      32
    :cy      78
    :flicker ["3.43s" "-.09s"]
    :hue     100
    :leaf    "hsl(100 65% 72%)"
    :pulse   "0s"
    :r       8.6
    :wobble  [-0.8 -1.5 0.7 -1.9 "2.54s" "-.12s"]}
   {:cx      32
    :cy      101
    :flicker ["3.68s" "-.77s"]
    :hue     78
    :leaf    "hsl(78 72% 70%)"
    :pulse   ".15s"
    :r       8.8
    :wobble  [0 -2 -0.3 -1.9 "2.01s" "-1.65s"]}
   {:cx      55
    :cy      124
    :flicker ["3.29s" "-1.33s"]
    :hue     61
    :leaf    "hsl(61 78% 69%)"
    :pulse   ".3s"
    :r       8.7
    :wobble  [-1.7 -1.2 0.6 2 "2.59s" "-1.95s"]}
   {:cx      78
    :cy      124
    :flicker ["3.67s" "-2.64s"]
    :hue     55
    :leaf    "hsl(55 80% 68%)"
    :pulse   ".45s"
    :r       8.5
    :wobble  [-2 1.6 -0.9 -1.6 "2.04s" "-1.63s"]}
   {:cx      101
    :cy      124
    :flicker ["3.57s" "-2.13s"]
    :hue     61
    :leaf    "hsl(61 78% 69%)"
    :pulse   ".6s"
    :r       7.8
    :wobble  [-1.4 0.4 0.6 -0.6 "2.56s" "-.12s"]}
   {:cx      124
    :cy      101
    :flicker ["2.92s" "-1.88s"]
    :hue     78
    :leaf    "hsl(78 72% 70%)"
    :pulse   ".75s"
    :r       8.9
    :wobble  [-1.3 0.8 -0.3 -0.8 "2.6s" "-.6s"]}
   {:cx      124
    :cy      78
    :flicker ["2.35s" "-2.62s"]
    :hue     100
    :leaf    "hsl(100 65% 72%)"
    :pulse   ".9s"
    :r       10
    :wobble  [1.3 0.9 -1.1 0.3 "2.53s" "-1.46s"]}
   {:cx      55
    :cy      32
    :flicker ["2.5s" "-1.36s"]
    :hue     100
    :leaf    "hsl(100 65% 72%)"
    :pulse   "1.1s"
    :r       8.1
    :wobble  [-0.9 2.1 -1.7 -0.4 "2.81s" "-.98s"]}
   {:cx      101
    :cy      32
    :flicker ["2.97s" "-1.89s"]
    :hue     100
    :leaf    "hsl(100 65% 72%)"
    :pulse   "1.1s"
    :r       8.5
    :wobble  [-2 0.7 1.2 0.3 "2.95s" "-1.39s"]}])


(def ^:private sparkles
  "[cx cy r delay duration]"
  [[25 18 4 "2s" "4s"]
   [133 48 3.5 "3.5s" "5.5s"]
   [78 62 4 "5s" "6s"]
   [14 114 3.5 "1s" "4.5s"]
   [144 130 4 "4.2s" "5s"]
   [140 14 3.5 ".3s" "4.8s"]
   [22 150 4 "2.8s" "5.2s"]])


(def ^:private stray-depths
  "Far strays are small and dim, near ones large and out of focus."
  {:far  {:fill "hsl(80 60% 70%)" :halo 4.5 :opacity 0.45 :r 1.4}
   :mid  {:fill "hsl(80 90% 75%)" :halo 7 :opacity 0.75 :r 2.2}
   :near {:blur "1.6px" :fill "hsl(80 90% 75%)" :halo 14.4 :opacity 0.5 :r 4.5}})


(def ^:private strays
  "Stray fireflies in a 390x844 frame, wandering 35-70 px:
   [depth cx cy dx dy duration delay [halo duration delay] [fly duration delay]]."
  [[:far 127 267 59 -26 "11.5s" "-5.9s" ["2.5s" "-2.6s"] ["4s" "-1.8s"]]
   [:mid 40 607 -37 -22 "6.6s" "-1.5s" ["3.7s" "-1s"] ["2.9s" "-.2s"]]
   [:near 76 133 -58 7 "7.9s" "-.8s" ["3.4s" "-2.5s"] ["2.7s" "-1.2s"]]
   [:far 351 119 -47 -20 "13.1s" "-5.6s" ["2.8s" "-2.9s"] ["3.8s" "-1.8s"]]
   [:mid 245 92 -67 -8 "6.7s" "-4.2s" ["2.9s" "-2.5s"] ["3.4s" "-.9s"]]
   [:near 119 264 -65 31 "8s" "-3.7s" ["2.6s" "-.6s"] ["3.9s" "-2.2s"]]
   [:far 65 665 -38 -35 "10.4s" "-2.2s" ["3.4s" "-.4s"] ["3s" "-2.7s"]]
   [:mid 199 248 -36 20 "8.7s" "-4.2s" ["3.9s" "-.1s"] ["3.5s" "-1.4s"]]
   [:near 189 161 -63 29 "7.1s" "-.3s" ["3.9s" "-2.2s"] ["2.7s" "-2.9s"]]
   [:far 204 581 -61 -37 "13.7s" "-3.4s" ["3.4s" "-1.1s"] ["3.4s" "-1.8s"]]
   [:mid 71 103 61 2 "7.2s" "-4.4s" ["3.4s" "-1.3s"] ["3.8s" "-.3s"]]])


(def ^:private leaves
  "Leaves cropped by the frame's edge: [x y angle length sway-duration sway-delay]."
  [[-70 150 -20 300 "7s" "0s"]
   [260 60 205 260 "7.9s" "-1.1s"]
   [280 330 165 240 "8.8s" "-2.2s"]
   [-90 590 15 320 "9.7s" "-3.3s"]
   [250 700 -150 300 "10.6s" "-4.4s"]
   [90 -60 65 220 "11.5s" "-5.5s"]
   [-40 860 -40 240 "12.4s" "-6.6s"]])


(defn- halo-gradient
  "A firefly's glow: a radial fade instead of an animated blur, which a
   phone would redraw every frame."
  [id hue inner?]
  [:radialGradient {:id id}
   [:stop {:offset "0" :stop-color (str "hsl(" hue " 95% 65%)") :stop-opacity ".9"}]
   (when inner?
     [:stop {:offset ".35" :stop-color (str "hsl(" hue " 95% 60%)") :stop-opacity ".35"}])
   [:stop {:offset "1" :stop-color (str "hsl(" hue " 95% 60%)") :stop-opacity "0"}]])


(defn- night
  "The dark scheme's background: strays under foliage."
  []
  [:svg.splash-night
   {:aria-hidden "true"
    :preserveAspectRatio "xMidYMid slice"
    :viewBox     "0 0 390 844"}
   [:defs (halo-gradient "splash-halo-stray" 80 false)]
   (for [[depth cx cy dx dy duration delay [halo-duration halo-delay] [fly-duration fly-delay]] strays
         :let [{:keys [blur fill halo opacity r]} (stray-depths depth)]]
     [:g.splash-stray
      {:style (cond-> {:--dx          (str dx "px")
                       :--dy          (str dy "px")
                       :--stray-delay delay
                       :--stray-duration duration
                       :opacity       opacity}
                blur (assoc :filter (str "blur(" blur ")")))}
      [:circle.splash-halo
       {:cx    cx
        :cy    cy
        :fill  "url(#splash-halo-stray)"
        :r     halo
        :style {:--flicker-delay    halo-delay
                :--flicker-duration halo-duration}}]
      [:circle.splash-fly
       {:cx    cx
        :cy    cy
        :fill  fill
        :r     r
        :style {:--flicker-delay    fly-delay
                :--flicker-duration fly-duration}}]])
   ;; Drawn above the strays, in the ground colour: a stray fades out behind
   ;; a leaf, and the leaf itself is never seen. A leaf lies along the x axis
   ;; from the origin: two cubic arcs, its width about two thirds of its length.
   [:g
    {:style {:filter "blur(3px)"}}
    (for [[x y angle length duration delay] leaves
          :let [quarter        (quot length 4)
                three-quarters (* 3 quarter)
                half-width     (Math/round (* 0.34 (double length)))]]
      [:g
       {:transform (str "translate(" x " " y ") rotate(" angle ")")}
       [:g.splash-leaf
        {:style {:--sway-delay    delay
                 :--sway-duration duration}}
        [:path
         {:d    (format "M0 0 C%1$d -%2$d %3$d -%2$d %4$d 0 C%3$d %2$d %1$d %2$d 0 0 Z"
                        quarter
                        half-width
                        three-quarters
                        length)
          :fill "#150f1c"}]]])]])


(defn- face
  "The ü: dots, their halos (dark scheme only) and purple sparkles."
  []
  [:svg.splash-face
   {:aria-hidden "true"
    :height      "156"
    :viewBox     "0 0 156 156"
    :width       "156"}
   [:defs
    (for [hue (distinct (map :hue dots))]
      (halo-gradient (str "splash-halo-" hue) hue true))]
   (for [{:keys [cx cy flicker hue leaf pulse r wobble]} dots
         :let [[ax ay bx by wobble-duration wobble-delay] wobble
               [flicker-duration flicker-delay] flicker]]
     [:g.splash-wobble
      {:style {:--ax (str ax "px")
               :--ay (str ay "px")
               :--bx (str bx "px")
               :--by (str by "px")
               :--firefly (str "hsl(" hue " 88% 72%)")
               :--flicker-delay flicker-delay
               :--flicker-duration flicker-duration
               :--leaf leaf
               :--pulse-delay pulse
               :--wobble-delay wobble-delay
               :--wobble-duration wobble-duration}}
      [:circle.splash-halo
       {:cx   cx
        :cy   cy
        :fill (str "url(#splash-halo-" hue ")")
        :r    (Math/round (* 2.8 (double r)))}]
      [:circle.dot
       {:cx cx
        :cy cy
        :r  r}]])
   (for [[cx cy r delay duration] sparkles]
     [:circle.sparkle
      {:cx    cx
       :cy    cy
       :r     r
       :style {:animation-delay    delay
               :animation-duration duration}}])])


(defn view
  "The splash, its stylesheet included: the page shows it until the app's
   script renders into the body."
  []
  [:<>
   [:style style]
   [:div.splash
    (night)
    [:div.splash-title "Sprecha"]
    (face)
    [:div.splash-sub
     [:i18n :shell/loading]]]])
