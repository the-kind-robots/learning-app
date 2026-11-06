(ns application.pages
  "The pages of the application. Each page declares itself in its view, a method of `page`;
   adding a page is one method there and a require in `main`.")


(defmulti page
  "The page of a name: `:path`; `:open` the action that computes the screen on entry; `:refresh` the action
   that computes it again from memory, given the context (or nil); `:hotkeys` a function of state giving the
   actions of each keystroke (`Alt+KeyW`: the modifiers, then the key's place, so any layout works); `:view` a
   function of state; and `:keyboard-overlays?` whether the virtual keyboard overlays the screen (absent: it
   resizes it).
   `:transitions` is how the screen moves on a phone (ADR-0024): `:in` coming on, `:out` going off, `:within` a
   function of the states before and after a render that stays on it, giving the transition or nil. Absent, the
   screen slides deeper as it opens over another and back as it is taken off (ADR-0026). Nil before a page is
   entered."
  identity)


(defmethod page :default
  [_]
  nil)


(def home
  "The page at the bottom of the stack of windows (ADR-0026): every other opens over it."
  :page/home)


(defn paths
  "The path of each page, as data for the navigation port; after the pages have declared themselves."
  []
  (->> (dissoc (methods page) :default)
       keys
       (map (fn [page-name]
              [page-name (:path (page page-name))]))
       (into {})))
