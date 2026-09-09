// The page's end of the dictionary. Every tab runs its own copy of this
// worker and the copies never speak to each other: which tab has the database
// open is settled by a Web Lock in sqlite3-dictionary.js, not by anything sent
// between them. A tab without the lock answers a query with an empty list
// rather than waiting for one.
//
// Two message paths to the page, and they are not alike.
//
// Standing — the worker's own port, the one `new Worker(...)` created. Status
// goes down it: loading, ready, error, phase. `self.postMessage` addresses
// exactly the one page that constructed this worker, and no other tab.
//
// Per query — the page makes a `MessageChannel`, keeps `port1`, and hands
// `port2` to `postMessage` as its second argument, the transfer list. It
// arrives here as `e.ports[0]`, and the answer goes straight back into it,
// where the page's `port1.onmessage` is the promise that asked. Request and
// reply are held together by the channel itself rather than by an id either
// side tracks — which is why there is no counter anywhere. A reply cannot be
// taken for a status message, and two queries in flight cannot be answered
// into each other.
//
//   https://developer.mozilla.org/en-US/docs/Web/API/Worker/postMessage
//   https://developer.mozilla.org/en-US/docs/Web/API/MessageEvent/ports
//   https://web.dev/articles/two-way-communication-guide
//
// Worth knowing before reading those: `Window.postMessage` takes the transfer
// list third, because `targetOrigin` is second. `Worker.postMessage` takes it
// second. Same method name, different signature.
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
  e.ports[0].postMessage(dictionary.request(e.data));
});
