(ns dev.devtools-socket
  "Where the shadow-cljs devtools socket connects: the page's own origin, and
   nothing else. Every host nginx serves proxies `/shadow-cljs/` to the watch,
   so whatever host the page came from is the right one. A fixed host compiled
   into the bundle was reachable from the desktop only, and a page opened on
   any other hostname never resolved it and showed «reconnecting» forever.

   A `:devtools :preloads` entry is resolved before
   `shadow.cljs.devtools.client.browser`, which opens the socket at load, so
   this assignment lands first (shadow.build.targets.browser, 3.4.12)."
  (:require
   [shadow.cljs.devtools.client.env :as env]))


(set! env/devtools-url (str js/location.origin "/shadow-cljs"))
