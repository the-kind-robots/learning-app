(ns ports.page
  "Whether the page is visible and the device online, as the use cases reach
   it. `adapters.page` does the work."
  (:require
   [adapters.page :as page]))


(defn start!
  [_deps]
  {:page/active?             page/active?
   :page/on-inactive         page/on-inactive
   :page/on-offline          page/on-offline
   :page/on-online           page/on-online
   :page/on-visibility-change page/on-visibility-change
   :page/online?             page/online?
   :page/until-active        page/until-active
   :page/visible?            page/visible?})
