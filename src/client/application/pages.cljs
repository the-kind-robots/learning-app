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
  "The pages by name. `:open` is the action that computes the screen when the
   page is entered; `:refresh` is a function of the state and the page's
   context that answers the actions that compute it again from memory, or
   nil (the lesson already on display is left as it is, and one with no
   words has nothing to compute); `:view` is a function of the state."
  {:page/collections {:open    :action/open-collections
                      :path    "/collections"
                      :refresh (fn [_ context]
                                 [[:action/show-collections context]])
                      :view    pages.collections.view/page}
   :page/home        {:open    :action/open-home
                      :path    "/home"
                      :refresh (fn [_ context]
                                 [[:action/show-home context]])
                      :view    pages.home.view/page}
   :page/lesson      {:open    :action/open-lesson
                      :path    "/lesson"
                      :refresh (fn [state _]
                                 (when (and (nil? (:lesson/state state))
                                            (not (:lesson/empty? state)))
                                   [[:effect/enter :action/open-lesson]]))
                      :view    pages.lesson.view/page}
   :page/words       {:open    :action/open-words
                      :path    "/words"
                      :refresh (fn [_ context]
                                 [[:action/show-words context]])
                      :view    pages.words.view/page}})


(def routes
  "The paths and the page names, as data for the browser port."
  (mapv (fn [[page {:keys [path]}]]
          [path {:name page}])
        table))
