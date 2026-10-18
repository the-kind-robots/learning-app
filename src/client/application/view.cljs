(ns application.view
  "The shell's markup: the bar with its icons, the sync menu and the QR
   dialog, the screen on display, and the status line."
  (:require
   [application.pages :as pages]
   [application.presenter :as presenter]
   [application.shell :as shell]
   [application.sync.presenter :as sync-presenter]
   [application.sync.view :as sync-view]
   [install-guide.view :as install-guide]))


(defn- collections-icon
  []
  [:button.app-shell__corner-icon
   {:type       "button"
    :aria-label "Открыть наборы"
    :title      "Наборы"
    :on         {:click [[:action/go-to-collections]]}}
   [:svg.app-shell__corner-icon-svg
    {:viewBox "0 0 16 16" :aria-hidden "true"}
    [:rect {:x 2 :y 2 :width 4 :height 4 :rx 1}]
    [:rect {:x 10 :y 2 :width 4 :height 4 :rx 1}]
    [:rect {:x 2 :y 10 :width 4 :height 4 :rx 1}]
    [:rect {:x 10 :y 10 :width 4 :height 4 :rx 1}]]])


(defn- close-icon
  []
  [:button.app-shell__corner-icon
   {:type       "button"
    :aria-label "Закрыть"
    :title      "Закрыть"
    :on         {:click [[:action/close-screen]]}}
   [:svg.app-shell__corner-icon-svg
    {:viewBox "0 0 16 16" :aria-hidden "true"}
    [:path
     {:d "M4 4 L12 12 M12 4 L4 12"
      :stroke "currentColor"
      :stroke-width 2
      :stroke-linecap "round"}]]])


(defn- install-icon
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}]
   [:polyline {:points "7 10 12 15 17 10"}]
   [:line {:x1 "12" :y1 "15" :x2 "12" :y2 "3"}]])


(defn- trace-export-icon
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"}]
   [:polyline {:points "14 3 14 8 19 8"}]
   [:line {:x1 "12" :y1 "11" :x2 "12" :y2 "17"}]
   [:polyline {:points "9 14 12 17 15 14"}]])


(defn shell
  "The whole page: the bar across the top, the dialogs, the screen the state
   is on, and the status line."
  [state]
  (let [{:keys [build-mark corner page read-error? show-install?]}
        (presenter/shell-props state)
        sync (sync-presenter/props state)]
    (list
     ;; One fixed bar holds the three slots: word mark, build mark, actions. The side slots share the free
     ;; space equally, which centres the build mark in the bar rather than between its neighbours.
     [:div.app-shell__bar
      [:div.app-shell__bar-slot
       [:button.app-shell__logo
        {:type "button"
         :on   {:click [[:action/go-to-home]]}}
        "Sprecha"
        ;; A development build's word mark ends in a red D; the ^boolean on the flag lets Closure drop it,
        ;; and the build mark below, from a release.
        (when ^boolean goog/DEBUG
          [:span.app-shell__dev-mark "D"])]]
      ;; Which bundle the page loaded, and the tap that reloads onto the newest one.
      (when ^boolean goog/DEBUG
        [:button.app-shell__build-mark
         {:type       "button"
          :aria-label "Перезагрузить сборку"
          :title      "Перезагрузить сборку"
          :on         {:click [[:effect/get-newest-build]]}}
         build-mark])
      [:div.app-shell__actions
       ;; The trace export leads the row: a development build's control among the shell's own.
       (when ^boolean goog/DEBUG
         [:button.app-shell__icon-button
          {:type       "button"
           :title      "Экспортировать трассу"
           :aria-label "Экспортировать трассу"
           :on         {:click [[:effect/export-trace]]}}
          (trace-export-icon)])
       ;; Install is not a sync action, and it is offered before any account exists.
       (when show-install?
         [:button.app-shell__icon-button
          {:type       "button"
           :title      "Установить приложение"
           :aria-label "Установить приложение"
           :on         {:click [[:action/pwa-install-requested]]}}
          (install-icon)])
       (sync-view/sync-button sync)
       (case corner
         :collections (collections-icon)
         :close       (close-icon)
         nil)]]
     (install-guide/render state)
     (sync-view/dialogs sync)
     (if-let [view (:view (pages/table page))]
       (view state)
       (when read-error?
         [:div.app-shell__read-error "Не получается прочитать данные на устройстве. Перезагрузите страницу."]))
     shell/status-region)))
