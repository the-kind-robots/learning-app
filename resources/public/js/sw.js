// SW_VERSION is prepended by the server: const SW_VERSION = "abcd1234";

const PRECACHE_URLS = [
  "/",
  "/css/base/colors.css",
  "/css/base/foundation.css",
  "/css/base/reset.css",
  "/css/base/typography.css",
  "/css/blocks/app-shell.css",
  "/css/blocks/home.css",
  "/css/blocks/lesson.css",
  "/css/blocks/modal.css",
  "/css/blocks/page-footer.css",
  "/css/blocks/pwa-install.css",
  "/css/blocks/vocabulary.css",
  "/css/blocks/word-edit-dialog.css",
  "/css/blocks/word-item.css",
  "/css/blocks/word-list.css",
  "/css/components/autocomplete.css",
  "/css/components/buttons.css",
  "/css/components/input.css",
  "/css/styles.css",
  "/favicon.ico",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-600.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-600italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2",
  "/icons.svg",
  "/icons/ue-192.png",
  "/icons/ue-512.png",
  "/js/app/main.js",
  "/manifest.json"
];

const PRECACHE_SET = new Set(PRECACHE_URLS);

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(SW_VERSION).then(cache =>
      cache.addAll(PRECACHE_URLS.map(url => new Request(url, { cache: "reload" })))
    )
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== SW_VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("message", event => {
  if (event.data?.type === "ping") {
    event.source?.postMessage({ type: "pong" });
  }
});

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  const cache = await caches.open(SW_VERSION);
  cache.put(request, response.clone());
  return response;
}

self.addEventListener("fetch", event => {
  const { request } = event;
  const path = new URL(request.url).pathname;
  if (!path.startsWith("/api/") && PRECACHE_SET.has(path)) {
    event.respondWith(cacheFirst(request));
  }
});
