(ns web-manifest
  "The web manifest the browser installs the app from (#816). It speaks the language the page speaks (#820):
   the install offer, the label under the icon and the shortcuts read in it, and the screenshots show the
   interface in it, or in English for a language added without its own; `scripts/install-screenshots.mjs` takes
   them. One `id` for every language keeps an installed app the same app."
  (:require
   [clojure.java.io :as io]
   [i18n :as i18n]))


(set! *warn-on-reflection* true)


(defn manifest
  "The manifest in `language`, with the launcher icons whose files start with `icon`: `ue` in production,
   `ue-dev` in a development build, whose icons carry a red D."
  [language icon]
  (i18n/interpolate
   language
   {:id          "/home"
    :description [:i18n :install/description]
    :display     "standalone"
    :icons       [{:sizes "192x192"
                   :src   (str "/icons/" icon "-192.png")
                   :type  "image/png"}
                  {:sizes "512x512"
                   :src   (str "/icons/" icon "-512.png")
                   :type  "image/png"}
                  {:purpose "maskable"
                   :sizes   "192x192"
                   :src     (str "/icons/" icon "-maskable-192.png")
                   :type    "image/png"}
                  {:purpose "maskable"
                   :sizes   "512x512"
                   :src     (str "/icons/" icon "-maskable-512.png")
                   :type    "image/png"}]
    :lang        language
    :name        [:i18n :install/name]
    ;; Phone screens only: on a computer the app is a narrow column in a wide frame, and Chrome keeps its short
    ;; dialog there (#816).
    :screenshots (for [[screen label] [["lesson" :install/screen-lesson]
                                       ["word" :install/screen-word]
                                       ["statistics" :install/screen-statistics]
                                       ["collections" :install/screen-collections]
                                       ["lesson-night" :install/screen-lesson-night]]
                       :let [path (str "/screenshots/" language "/" screen ".png")]]
                   {:form_factor "narrow"
                    :label       [:i18n label]
                    :sizes       "1170x2532"
                    :src         (if (io/resource (str "public" path))
                                   path
                                   (str "/screenshots/en/" screen ".png"))
                    :type        "image/png"})
    :short_name  [:i18n :shell/word-mark]
    :shortcuts   [{:icons [{:sizes "192x192"
                            :src   "/icons/shortcut-lesson.png"
                            :type  "image/png"}
                           {:purpose "maskable"
                            :sizes   "192x192"
                            :src     "/icons/shortcut-lesson-maskable.png"
                            :type    "image/png"}]
                   :name  [:i18n :lesson/title]
                   :url   "/lesson"}
                  {:icons [{:sizes "192x192"
                            :src   "/icons/shortcut-words.png"
                            :type  "image/png"}
                           {:purpose "maskable"
                            :sizes   "192x192"
                            :src     "/icons/shortcut-words-maskable.png"
                            :type    "image/png"}]
                   :name  [:i18n :install/shortcut-words]
                   :url   "/words"}]
    :start_url   "/home"}))
