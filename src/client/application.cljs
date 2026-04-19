(ns application
  (:require
   [dbs :as dbs]
   [dictionary :as dictionary]
   [dictionary-sync :as dictionary-sync]
   [domain.vocabulary :as domain.vocabulary]
   [examples :as examples]
   [hiccup :as hiccup]
   [lesson :as lesson]
   [presenter.vocabulary :as presenter.vocabulary]
   [promesa.core :as p]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [utils :as utils]
   [views.app-install-guide :as app-install-guide]
   [views.dictionary :as views.dictionary]
   [views.home :as views.home]
   [views.lesson :as views.lesson]
   [views.vocabulary :as views.word]
   [vocabulary :as vocabulary]))


;;
;; Layout
;;


(defn- app-shell
  [{:html/keys [head body]}]
  (list
   [:!DOCTYPE "html"]
   [:html
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta
      {:name    "viewport"
       :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
     [:title "Sprecha"]
     [:link {:rel "icon" :href "/favicon.ico"}]
     [:link {:rel "manifest" :href "/manifest.json"}]
     [:link {:rel "stylesheet" :href "/css/styles.css"}]
     [:script {:src "/js/htmx/htmx.min.js" :defer true}]
     [:script {:src "/js/htmx/class-tools.js" :defer true}]
     [:script {:src "/js/word-autocomplete.js" :defer true}]
     [:script {:src "/js/virtual-keyboard.js" :defer true}]
     [:script {:src "/js/pwa-install.js" :defer true}]
     head]
    [:body
     [:div#loader.app-shell__loader
      [:div.app-shell__loader-list
       {:style {:--items-count 1}}
       [:div.app-shell__loader-text "Загружаем..."]]]
     [:a.app-shell__logo
      {:href        "/home"
       :hx-get      "/home"
       :hx-push-url "true"
       :hx-swap     "innerHTML"
       :hx-target   "#app"}
      "Sprecha"]
     [:button#install-button.app-shell__install-button
      {:hidden      true
       :hx-on:click "htmx.trigger(window, 'app:install-requested')"}
      "УСТАНОВИТЬ"]
     (app-install-guide/view)
     [:dialog#modal.modal
      {:hx-on:htmx:after-swap "if(!this.open) this.showModal()"
       :hx-on:click           "if(event.target===this) this.close()"}]
     [:div#app body]]]))


;;
;; Routes
;;


(def page-layout-interceptor
  {:name  ::page-layout-interceptor
   :leave (fn [ctx]
            (let [request  (:request ctx)
                  response (:response ctx)]
              (if (or (-> request :headers (get "hx-request"))
                      (nil? (:html/body response)))
                ctx
                (assoc-in ctx [:response :html/layout] app-shell))))})


(def db-interceptor
  "Injects database instances into request."
  {:name  ::db-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :dbs] (dbs/dbs)))})


(def dictionary-sync-interceptor
  "Retries dictionary sync if it failed (e.g. server was unavailable)."
  {:name  ::dictionary-sync-interceptor
   :enter (fn [ctx]
            (when-not (dictionary-sync/loaded?)
              (dictionary-sync/ensure-loaded!))
            ctx)})


(def ui-routes
  [[""
    ["/home"
     {:get (fn [{:keys [dbs]}]
             (p/let [total (vocabulary/count dbs)]
               {:html/body (views.home/page :empty-vocab? (zero? total))}))}]

    ["/dictionary-entries"
     {:get (fn [{:keys [dbs params]}]
             (-> (p/let [{:keys [suggestions prefill]}
                         (dictionary/suggest dbs (:value params))]
                   {:html/body (views.dictionary/suggestions suggestions prefill)
                    :status    200})
                 (p/catch
                   (fn [_err]
                     {:html/body (views.dictionary/suggestions [] nil)
                      :status    200}))))}]

    ["/words"
     {:head (fn [{:keys [dbs]}]
              (p/let [total (vocabulary/count dbs)]
                {:status  200
                 :headers {"X-Total-Count" (str total)}}))

      :get  (fn [{:keys [dbs params headers]}]
              (let [{:keys [offset limit search fragment]} params
                    htmx-target (get headers "hx-target")
                    offset      (utils/parse-int offset 0)
                    limit       (utils/parse-int limit 10)
                    chunk?      (= "chunk" fragment)
                    words-query {:order  :asc
                                 :limit  limit
                                 :offset offset
                                 :search search}]
                (p/let [{:keys [words total]} (vocabulary/list dbs words-query)]

                  (let [words      (presenter.vocabulary/word-list-props words)
                        show-more? (> total (+ offset limit))
                        list-opts  {:words-query words-query
                                    :search      search
                                    :show-more?  show-more?
                                    :words       words}]
                    {:status    200
                     :html/body (cond
                                  chunk?
                                  (or (views.word/word-list-chunk list-opts) "")

                                  (= htmx-target "word-list")
                                  (views.word/word-list list-opts)

                                  :else
                                  (views.word/page
                                   {:empty?     (zero? total)
                                    :search     search
                                    :words      words
                                    :show-more? show-more?}))}))))

      :post (fn [{:keys [dbs params]}]
              (let [{:keys [value translation]} params
                    result (domain.vocabulary/validate-new-word value translation)]
                (if-let [error (:error result)]
                  {:html/body (views.word/validation-error-inputs error)
                   :status    400}
                  (p/let [total  (vocabulary/count dbs)
                          result (vocabulary/add! dbs value translation)]
                    (if (:error result)
                      {:html/body (views.word/validation-error-inputs {:translation-blank? true})
                       :status    400}
                      (let [{:keys [word-id created?]} result
                            first-word? (and created? (zero? total))]
                        (when created?
                          (examples/create-fetch-task! word-id))
                        {:html/body (views.home/add-success :first-word? first-word?)
                         :status    201}))))))}]

    ["/words/:id/edit"
     {:get (fn [{:keys [dbs path-params]}]
             (p/let [word (vocabulary/get dbs (:id path-params))]
               (if word
                 {:status    200
                  :html/body (views.word/edit-form
                              (presenter.vocabulary/word-item-props word))}
                 {:status 404})))}]

    ["/words/:id"
     {:put    (fn [{:keys [dbs path-params params]}]
                (let [word-id     (:id path-params)
                      translation (:translation params)
                      result      (domain.vocabulary/validate-word-update
                                   {:word-id word-id :translation translation})]
                  (if-let [error (:error result)]
                    {:status 400 :body error}
                    (p/let [word (vocabulary/update! dbs word-id translation)]
                      (if word
                        {:html/body (views.word/word-list-item
                                     (presenter.vocabulary/word-item-props word))
                         :status    200}
                        {:status 404})))))

      :delete (fn [{:keys [dbs path-params]}]
                (let [word-id (:id path-params)]
                  (p/do
                    (vocabulary/delete! dbs word-id)
                    (p/let [total (vocabulary/count dbs)]
                      (if (zero? total)
                        {:headers   {"HX-Retarget" "#app" "HX-Reswap" "innerHTML"}
                         :html/body (views.word/page {:empty? true})
                         :status    200}
                        {:status    200
                         :html/body [:li {:id          (str "word-" word-id)
                                          :hx-swap-oob "delete"}]})))))}]

    ["/lesson"
     {:get    (fn [{:keys [dbs]}]
                (p/let [{:keys [lesson-state error]} (lesson/restart! dbs {:trial-selector :random})]
                  (cond
                    lesson-state
                    {:html/body (views.lesson/page lesson-state) :status 200}

                    (= error :no-words-available)
                    {:html/body (views.lesson/empty-state) :status 200}

                    :else
                    (throw (ex-info "Failed to create lesson" {:error error})))))

      :delete (fn [{:keys [dbs]}]
                (p/do
                  (lesson/finish! dbs)
                  (p/let [total (vocabulary/count dbs)]
                    {:headers   {"HX-Push-Url" "/home"}
                     :html/body (views.home/page :empty-vocab? (zero? total))
                     :status    200})))}]

    ["/lesson/answer"
     {:post (fn [{:keys [dbs params]}]
              (let [answer (:answer params)]
                (p/let [{:keys [lesson-state]} (lesson/check-answer! dbs answer)]
                  (if lesson-state
                    {:html/body (list
                                 (views.lesson/footer lesson-state)
                                 (views.lesson/challenge lesson-state {:hx-swap-oob "true"})
                                 (views.lesson/progress lesson-state {:hx-swap-oob "innerHTML"}))
                     :status    200}
                    {:status 404}))))}]

    ["/lesson/next"
     {:post (fn [{:keys [dbs]}]
              (p/let [{:keys [lesson-state]} (lesson/advance! dbs)]
                (if lesson-state
                  {:html/body (list
                               (views.lesson/input)
                               (views.lesson/challenge lesson-state {:hx-swap-oob "true"})
                               (views.lesson/progress lesson-state {:hx-swap-oob "innerHTML"}))
                   :status    200}
                  {:status 404})))}]]])


(def ring-handler
  (http/ring-handler
   (http/router
    ui-routes

    {:data {:interceptors [db-interceptor
                           dictionary-sync-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)
                           (hiccup/interceptor {:layout-fn nil})
                           page-layout-interceptor]}})

   (constantly {:status 404 :body ""})

   {:executor sieppari/executor}))
