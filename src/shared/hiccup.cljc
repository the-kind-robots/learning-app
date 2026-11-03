;; Adapted from https://github.com/lambdaisland/hiccup/blob/main/src/lambdaisland/hiccup/middleware.clj
(ns hiccup
  (:require
   [clojure.string :as str]
   #?(:clj [lambdaisland.hiccup :as hiccup]
      :cljs ["@thi.ng/hiccup" :as hiccup])))


(def render
  #?(:clj hiccup/render
     :cljs (comp hiccup/serialize clj->js)))


(defn default-layout
  "Basic layout function used when no specific layout is provided."
  [{:html/keys [head body]}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta
     {:name    "viewport"
      :content "width=device-width, initial-scale=1"}]
    head]
   [:body
    body]])


(defn render-response
  ([res req]
   (render-response res req {}))
  ([res req
    {:keys [layout-fn route-data-fn accept content-type]
     :or   {layout-fn     default-layout
            route-data-fn (comp :data :reitit.core/match)
            accept        #{"text/html"}
            content-type  "text/html; charset=utf-8"}}]
   (let [route-data    (when route-data-fn (route-data-fn req))
         head          (or (:html/head res)
                           (:html/head route-data))
         layout-fn     (first (filter some?
                                      [(:html/layout res)
                                       (:html/layout route-data)
                                       layout-fn]))
         body          (:html/body res)
         accept-header (get-in req [:headers "accept"])]
     ;; Render HTML if there's a `:html/body` key, and the client accepts
     ;; text/html, OR there is no `:body` key, because if there isn't then
     ;; there is nothing else to fall back to.
     (if (and body
              (or (not (:body res))
                  (and accept-header (some accept (str/split accept-header #",")))))
       (-> res
           (assoc :status (or (:status res) 200)
                  :body   (render (if layout-fn
                                    (layout-fn (assoc req :html/head head :html/body body))
                                    body)))
           (assoc-in [:headers "content-type"] content-type))
       res))))


(defn wrap-render
  "Content negotiation for html/hiccup.

  If the client accepts \"text/html\", and the response contains a
  `:html/body` (hiccup form), then use it to render a HTML response. If the
  client asks for a different media type, then the request is passed on for
  other middleware to act upon, e.g. render the `:body` with Muuntaja.

  Additionally `:html/layout` and `:html/head` can be provided, either in the
  response map, or in (Reitit) route data. Layout-fn receives the request map
  plus two additional keys, `:html/body` and `:html/head`, both containing a
  hiccup element or fragment, which should be inserted in the head and body
  elements respectively.

  `:html/head` can be used to set the page title, social media tags, etc.

  The response status if not set defaults to 200.

  Middleware options

  - `:layout-fn` - fallback layout function
  - `:route-data-fn` - function from request map to route data
  - `:accept` - set of mime types that should trigger hiccup rendering
  - `:content-type` - content-type of the response
  "
  ([handler]
   (wrap-render handler nil))
  ([handler opts]
   (fn [req]
     (let [res (handler req)]
       (render-response res req opts)))))


(defn interceptor
  ([]
   (interceptor {}))
  ([opts]
   {:name  ::render
    :leave (fn [ctx]
             (let [req (:request ctx)]
               (update ctx :response render-response req opts)))}))
