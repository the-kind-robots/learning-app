(ns application.pages
  "Every page of the application, written once: its path, the action that
   opens it when it is entered, how the screen on display is computed again
   from memory, its hotkeys, and its view. Adding a page is one entry here."
  (:require
   [domain.lesson :as lesson]
   [pages.collections.view]
   [pages.home.view]
   [pages.lesson.view]
   [pages.words.view]))


(def table
  "The pages by name: `:open` the action that computes the screen on entry, `:refresh` a function of state
   and context that computes it again from memory (or nil), `:hotkeys` a function of state giving the actions of
   each keystroke (`Alt+KeyW`: the modifiers, then the key's place, so any layout works), `:view` a function of
   state, and `:keyboard-overlays?` whether the virtual keyboard overlays the screen (absent: it resizes it).
   Home and the words list overlay it: the keyboard covers the lesson button fixed at the bottom, and the search bar
   closes the column on the keyboard's inset.
   `:transitions` is how the screen moves on a phone (ADR-0024): `:in` coming on, `:out` going off, `:within` a
   function of the states before and after a render that stays on it, giving the transition or nil. Absent, the
   screen slides deeper and back."
  {:page/collections {:hotkeys (constantly {"Escape" [[:action/close-screen]]})
                      :open    :action/open-collections
                      :path    "/collections"
                      :refresh (fn [_ context]
                                 [[:action/show-collections context]])
                      :view    pages.collections.view/page}
   ;; A keystroke offers no more than the screen does: an empty vocabulary hides the lesson and words buttons.
   :page/home        {:hotkeys (fn [state]
                                 (merge {"Alt+KeyT" [[:action/go-to-collections]]}
                                        (when-not (:home/empty-vocab? state)
                                          {"Alt+Enter" [[:action/go-to-lesson]]
                                           "Alt+KeyW"  [[:action/go-to-words]]})))
                      :keyboard-overlays? true
                      :open    :action/open-home
                      :path    "/home"
                      :refresh (fn [_ context]
                                 [[:action/show-home context]])
                      :view    pages.home.view/page}
   ;; A lesson is a mode, not a step deeper: it fades in and out. Within it, «ДАЛЕЕ» moves the trial.
   :page/lesson      {:open        :action/open-lesson
                      :path        "/lesson"
                      :refresh     (fn [state _]
                                     (when (and (nil? (:lesson/state state))
                                                (not (:lesson/empty? state)))
                                       [[:effect/enter :action/open-lesson]]))
                      :transitions {:in     :transition/fade-in
                                    :out    :transition/fade-out
                                    :within (fn [before after]
                                              (when (and (lesson/last-result (:lesson/state before))
                                                         (nil? (lesson/last-result (:lesson/state after))))
                                                :transition/next-trial))}
                      :view        pages.lesson.view/page}
   ;; An open word dialog takes `Escape` itself and covers the lesson button; an empty vocabulary hides it.
   :page/words       {:hotkeys (fn [state]
                                 (when-not (:words/editing state)
                                   (merge {"Escape" [[:action/close-screen]]}
                                          (when (some-> (:words/total state) pos?)
                                            {"Alt+Enter" [[:action/go-to-lesson]]}))))
                      :keyboard-overlays? true
                      :open    :action/open-words
                      :path    "/words"
                      :refresh (fn [_ context]
                                 [[:action/show-words context]])
                      :view    pages.words.view/page}})


(def home
  "The page the history rests on (ADR-0015): Back from any other page lands on it."
  :page/home)


(def routes
  "The paths and the page names, as data for the navigation port."
  (mapv (fn [[page {:keys [path]}]]
          [path {:name page}])
        table))
