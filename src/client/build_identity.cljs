(ns build-identity
  "Which build the page is running: the short commit of the checkout it was
   compiled from, a `+` when that tree had uncommitted changes, and the day,
   month and time it was compiled. The `dev.build-identity` build hook writes it
   on every rebuild; a release build leaves it empty, and every reader is
   dropped with `goog/DEBUG`.

   Fixed at page load, not live. The value comes from CLOSURE_DEFINES in the
   module file, which the page reads once, so after a hot reload it still names
   the bundle the page started on — which is the question it answers: is this
   device on the new build or still on the old one.")


(goog-define stamp "")
