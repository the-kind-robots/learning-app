;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [clojure.string :as str]
   [lambdaisland.glogi :as log]
   [logging])
  (:require-macros [sw-version]))


(def precache
  (sw-version/precache-manifest
   ["/"
    "/css/base/colors.css"
    "/css/base/foundation.css"
    "/css/base/reset.css"
    "/css/base/typography.css"
    "/css/blocks/app-shell.css"
    "/css/blocks/home.css"
    "/css/blocks/lesson.css"
    "/css/blocks/modal.css"
    "/css/blocks/page-footer.css"
    "/css/blocks/pwa-install.css"
    "/css/blocks/vocabulary.css"
    "/css/blocks/word-edit-dialog.css"
    "/css/blocks/word-item.css"
    "/css/blocks/word-list.css"
    "/css/components/autocomplete.css"
    "/css/components/buttons.css"
    "/css/components/input.css"
    "/css/styles.css"
    "/favicon.ico"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-600.woff2"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-600italic.woff2"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2"
    "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2"
    "/icons.svg"
    "/icons/ue-192.png"
    "/icons/ue-512.png"
    "/js/pwa-install.js"
    "/manifest.json"]))


(def version (:hash precache))


(def base-precache-urls (:list precache))


(js/self.addEventListener
 "install"
 (fn [event]
   (log/debug :event/install event)
   (.waitUntil
    event
    ((fn ^:async event-install []
       (let [cache (await (js/caches.open "resources"))]
         (await (.addAll cache
                         (to-array (map #(js/Request. % #js {:cache "reload"})
                                        base-precache-urls))))))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   (.waitUntil
    event
    ((fn ^:async f []
       (let [keys (await (js/caches.keys))]
         (await (js/Promise.all
                 (to-array (keep #(when-not (= % "resources")
                                    (js/caches.delete %))
                                 keys)))))
       (await (js/self.clients.claim)))))))


(js/self.addEventListener
 "message"
 (fn [event]
   (case (.. event -data -type)
     "ping" (some-> (.-source event)
                    (.postMessage #js {:type "pong"}))
     nil)))


(defn- static-request?
  [request]
  (let [path (.-pathname (js/URL. (.-url request)))]
    (contains? (set base-precache-urls) path)))


(defn- api-request?
  [request]
  (str/starts-with? (.-pathname (js/URL. (.-url request))) "/api/"))


(defn- cache-first
  [request]
  ((fn ^:async f []
     (let [cached (await (js/caches.match request))]
       (if cached
         cached
         (let [response (await (js/fetch request))
               cache    (await (js/caches.open "resources"))]
           (.put cache request (.clone response))
           response))))))


(js/self.addEventListener
 "fetch"
 (fn [event]
   (let [request (.-request event)]
     (when (and (not (api-request? request))
                (static-request? request))
       (.respondWith event (cache-first request))))))
