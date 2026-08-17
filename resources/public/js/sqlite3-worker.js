// The page's end of the dictionary. Every tab has one of these and they never
// speak to each other; which of them has the database open is decided by the
// browser's lock manager, and one without the lock holds its queries until
// its turn comes. Neither this file nor the page can tell the two apart —
// that is the point of #351.
//
// The page does have to say one thing about itself: whether it is the tab in
// front. Only that tab may hold the database — a backgrounded one is frozen
// with it, and a merely visible one is not necessarily the one being typed
// into. How the page decides is the page's business; here it is one boolean.

importScripts("sqlite3-dictionary.js");

dictionary.start((msg) => self.postMessage(msg));

self.addEventListener("message", (e) => {
  if (e.data.type === "foreground") {
    dictionary.pageIsForeground(e.data.foreground);
    return;
  }
  dictionary.request(e.data).then((response) => self.postMessage(response));
});
