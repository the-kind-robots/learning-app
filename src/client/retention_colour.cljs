(ns retention-colour
  "The colour of how well a word is remembered, one rule for every screen that shows it: the word list's mark and
   the statistics' bars, strip and dots. Neon at none, thallium at all.")


(defn at
  "The colour at `percent` of retention, 0 to 100, as CSS reads it."
  [percent]
  (let [percent (-> percent (or 0) (max 0) (min 100))]
    (str "color-mix(in hsl, rgb(var(--color-thallium)) " percent "%, rgb(var(--color-neon)))")))
