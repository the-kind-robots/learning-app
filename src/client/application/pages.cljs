(ns application.pages
  "Every page of the application, written once: its path, the action that
   opens it when it is entered, how the screen on display is computed again
   from memory, and its view. Adding a page is one entry here."
  (:require
   [pages.collections.view]
   [pages.home.view]
   [pages.lesson.view]
   [pages.words.view]))


(def table
  "The pages by name: `:open` the action that computes the screen on entry, `:refresh` a function of state
   and context that computes it again from memory (or nil), `:view` a function of state, and `:keyboard-overlays?`
   whether the virtual keyboard overlays the screen (absent: it resizes it). Home and the words list overlay it: the
   keyboard covers the lesson button fixed at the bottom, and the search bar closes the column on the keyboard's inset."
  {:page/collections {:open    :action/open-collections
                      :path    "/collections"
                      :refresh (fn [_ context]
                                 [[:action/show-collections context]])
                      :view    pages.collections.view/page}
   :page/home        {:keyboard-overlays? true
                      :open               :action/open-home
                      :path               "/home"
                      :refresh            (fn [_ context]
                                            [[:action/show-home context]])
                      :view               pages.home.view/page}
   :page/lesson      {:open    :action/open-lesson
                      :path    "/lesson"
                      :refresh (fn [state _]
                                 (when (and (nil? (:lesson/state state))
                                            (not (:lesson/empty? state)))
                                   [[:effect/enter :action/open-lesson]]))
                      :view    pages.lesson.view/page}
   :page/words       {:keyboard-overlays? true
                      :open               :action/open-words
                      :path               "/words"
                      :refresh            (fn [_ context]
                                            [[:action/show-words context]])
                      :view               pages.words.view/page}})


(def home
  "The page the history rests on (ADR-0015): Back from any other page lands on it."
  :page/home)


(def routes
  "The paths and the page names, as data for the navigation port."
  (mapv (fn [[page {:keys [path]}]]
          [path {:name page}])
        table))
