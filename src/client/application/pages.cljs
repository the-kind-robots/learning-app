(ns application.pages
  "The pages of the application. Each page declares itself in its view, a method of `page`;
   adding a page is one method there and a require in `main`.")


(defmulti page
  "The page of a name: `:path`, `:view`, `:hotkeys`, `:keyboard-overlays?`, `:transitions` (ADR-0024), and the
   actions that compute its screen: `:open` as its window opens, `:refresh` after the load and the reader's own
   change."
  identity)


(defmethod page :default
  [_]
  nil)


(def home
  "The page at the bottom of the stack of windows (ADR-0026): every other opens over it."
  :page/home)


(defn shown
  "The page on display: the top of the stack of open windows (ADR-0029)."
  [state]
  (peek (:page/windows state)))


(defn paths
  "The path of each page, as data for the navigation port; after the pages have declared themselves."
  []
  (->> (dissoc (methods page) :default)
       keys
       (map (fn [page-name]
              [page-name (:path (page page-name))]))
       (into {})))
