(ns colour-scheme
  "The colour scheme picked on a device, as the server and the client agree on it: the cookie that keeps the pick
   and the media under which the night applies (ADR-0027).")


(def cookie
  "The cookie that keeps the device's pick, `light` or `dark`; without it the device follows the system. The client
   writes it, the server renders the page from it."
  "scheme")


(defn picked
  "The scheme the cookie's `value` names: `:scheme/light`, `:scheme/dark`, else `:scheme/system`, the system's."
  [value]
  (get {"dark" :scheme/dark "light" :scheme/light} value :scheme/system))


(defn night-media
  "The media of the night under `scheme`: all for dark, none for light, else where the system prefers dark. The
   page's night stylesheet, the night's `theme-color` and the splash's night rules carry it, each marked
   `data-night`."
  [scheme]
  (case scheme
    :scheme/dark  "all"
    :scheme/light "not all"
    "(prefers-color-scheme: dark)"))
