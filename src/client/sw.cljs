;;
;; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
;;
(ns sw
  (:require
   [application :as application]
   [clojure.string :as str]
   [db-migrations :as db-migrations]
   [dictionary-sync :as dictionary-sync]
   [lambdaisland.glogi :as log]
   [logging]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils])
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
    "/css/blocks/page-footer.css"
    "/css/blocks/pwa-install.css"
    "/css/blocks/vocabulary.css"
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
    "/js/htmx/class-tools.js"
    "/js/htmx/htmx.min.js"
    "/js/pwa-install.js"
    "/js/sw-loader.js"
    "/js/virtual-keyboard.js"
    "/js/word-autocomplete.js"
    "/manifest.json"]))


(def version (:hash precache))


(def base-precache-urls (:list precache))


;;
;; Listeners
;;


(js/self.addEventListener
 "install"
 (fn [^InstallEvent event]
   (log/debug :event/install event)
   (..
    event
    (waitUntil
     (p/do
       (p/let [cache (js/caches.open "resources")]
         (.addAll cache
                  (to-array (map #(js/Request. % #js {:cache "reload"})
                                 base-precache-urls)))))))))


(js/self.addEventListener
 "activate"
 (fn [event]
   (log/debug :event/activate event)
   (..
    event
    (waitUntil
     (-> (p/do
           (db-migrations/ensure-migrated!)
           (tasks/start!)
           (dictionary-sync/ensure-loaded!)
           (js/self.clients.claim))
         (p/catch
           (fn [err]
             (log/error :sw/activate-failed {:error (str err)})
             (throw err))))))))


(js/self.addEventListener
 "message"
 (fn [event]
   (case (.. event -data -type)
     "ping" (some-> (.-source event)
                    (.postMessage #js {:type "pong"}))
     nil)))


(js/self.addEventListener
 "online"
 (fn [_event]
   (tasks/resume!)))


(defn- static-request?
  [request]
  (let [path (.-pathname (js/URL. (.-url request)))]
    (contains? (set base-precache-urls) path)))


(defn- api-request?
  "Returns true if this is a request to the backend API (e.g., /api/examples).
   API requests should be network-only, not cached."
  [request]
  (let [path (.-pathname (js/URL. (.-url request)))]
    (str/starts-with? path "/api/")))


(defn network-first-fetch
  [request]
  (p/catch
    (p/let [response (js/fetch request)
            cache    (js/caches.open "resources")]
      (.put cache request (.clone response))
      response)
    (fn [_]
      (js/caches.match request))))


(defn- request->ring
  "Transforms a JavaScript [Request](https://github.com/ring-clojure/ring/wiki/Concepts#requests) object into a Ring-style request hash map."
  [request]
  (let [request    (.clone request)
        url-object (js/URL. (.-url request))
        headers    (-> request .-headers .entries js/Object.fromEntries js->clj)
        headers    (update-keys headers str/lower-case)]
    {:server-port    (.-port url-object)
     :server-name    (.-hostname url-object)
     :uri            (.-pathname url-object)
     :url            (.-url request)
     :query-string   (utils/non-blank (.-search url-object))
     :scheme         (-> url-object .-protocol (str/replace #":" "") keyword)
     :request-method (-> request .-method str/lower-case keyword)
     :headers        headers
     :body           (.-body request)
     ;; Custom non-ring fields
     :js/request     request}))


(defn- ring->response
  "Transforms a Ring-style response hash map into JavaScript [Response](https://developer.mozilla.org/en-US/docs/Web/API/Response/Response) object into a Ring-style."
  [ring-response]
  (when-not (= (:status ring-response) 404)
    (js/Response.
     (:body ring-response)
     (clj->js
      (select-keys ring-response [:status :headers])))))


(defn local-handler
  [request]
  (let [ring-request (request->ring request)]
    (p/-> (application/ring-handler ring-request) ring->response)))


(js/self.addEventListener
 "fetch"
 (fn [^FetchEvent event]
   (log/debug :event/fetch event)
   (..
    event
    (respondWith
     (p/let [request (.. event -request)]
       (cond
         (api-request? request)    (js/fetch request)
         (static-request? request) (network-first-fetch request)
         :else                     (local-handler request)))))))
