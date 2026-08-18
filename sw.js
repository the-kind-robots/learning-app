// Precache of a fixed list, so the running app makes no network request.
//
// Navigations go to the network first and fall back to the cached document;
// subresources are cache-first, which is what keeps the running app off the
// network. Reloads were only ever seen to commit with the navigation on the
// network path.

const CACHE  = "sah-control-v5";
const ASSETS = ["./", "./index.html", "./app.js", "./db-worker.js",
                "./sqlite3.js", "./sqlite3.wasm", "./manifest.webmanifest",
                "./icon-192.png", "./icon-512.png", "./icon-maskable-512.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE)
    .then((cache) => cache.addAll(ASSETS))
    .then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;

  if (e.request.mode === "navigate") {
    e.respondWith(fetch(e.request).catch(() => caches.match("./index.html")));
    return;
  }

  e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request)));
});
