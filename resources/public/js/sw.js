// SW_VERSION is prepended by the server: const SW_VERSION = "abcd1234";
//
// SW_VERSION doubles as the cache bucket name. Each deploy gets a fresh bucket;
// the activate handler deletes every bucket whose name isn't SW_VERSION, so
// stale assets from old deployments are evicted automatically.

// Static app-shell resources.
//
// These are safe to cache during SW install and serve cache-first because their
// lifecycle is tied to SW_VERSION. Keep dynamic/runtime metadata out of this
// list; it needs its own cache policy.
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
  "/css/blocks/popover.css",
  "/css/blocks/pwa-install.css",
  "/css/blocks/token-card.css",
  "/css/blocks/vocabulary.css",
  "/css/blocks/word-edit-dialog.css",
  "/css/blocks/word-item.css",
  "/css/blocks/word-list.css",
  "/css/components/autocomplete.css",
  "/css/components/buttons.css",
  "/css/components/input.css",
  "/css/styles.css",
  "/favicon.ico",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-200.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-200italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-300.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-300italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-500italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-600.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-600italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-700italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-800italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-900.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-900italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-italic.woff2",
  "/fonts/Nunito/nunito-v26-cyrillic_latin-regular.woff2",
  "/dev-manifest.json",
  "/icons.svg",
  "/icons/cancel.svg",
  "/icons/ue-192.png",
  "/icons/ue-512.png",
  "/icons/ue-dev-192.png",
  "/icons/ue-dev-512.png",
  "/js/app/main.js",
  "/js/sqlite3-worker.js",
  "/js/sqlite3.js",
  "/js/sqlite3.wasm",
  "/manifest.json"
];

// Set for O(1) path lookup in the fetch handler.
const PRECACHE_SET = new Set(PRECACHE_URLS);

// The cached document shell. On offline navigation reloads, the SW returns this
// static HTML only; main.cljs still owns routing and UI rendering.
const APP_SHELL_URL = "/";

// Dynamic dictionary boot metadata. This is deliberately not in PRECACHE_URLS:
// the dictionary version can change independently from resources/public, and
// online boots must see the freshest manifest while offline boots may use the
// last cached manifest for an already-imported OPFS dictionary.
const DICTIONARY_MANIFEST_URL = "/dictionary/manifest";

function sameOrigin(url) {
  return url.origin === self.location.origin;
}

// Matches SPA navigations that should be handled by the app shell, not the server.
// API/auth/dictionary paths are excluded so their network failures surface to app logic.
function appNavigation(request, url) {
  if (request.method !== "GET") return false;
  if (!sameOrigin(url)) return false;
  if (request.mode !== "navigate") return false;
  if (url.pathname.startsWith("/api/")) return false;
  if (url.pathname.startsWith("/auth/")) return false;
  if (url.pathname.startsWith("/dictionary/")) return false;
  if (url.pathname.startsWith("/db/")) return false;
  return true;
}

function dictionaryManifestRequest(request, url) {
  return request.method === "GET"
    && sameOrigin(url)
    && url.pathname === DICTIONARY_MANIFEST_URL;
}

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(SW_VERSION).then(cache =>
      // { cache: "reload" } bypasses the HTTP cache so precached assets are
      // always fresh at install time, not served from a stale browser cache.
      cache.addAll(PRECACHE_URLS.map(url => new Request(url, { cache: "reload" })))
    )
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== SW_VERSION).map(k => caches.delete(k))))
      // claim() takes control of already-open tabs immediately, without waiting
      // for them to reload — so they get the new SW's fetch handler right away.
      .then(() => self.clients.claim())
  );
});


async function cacheFirst(request, cacheKey) {
  const cached = await caches.match(request);
  if (cached) return cached;

  // Fallback to bare path lookup: handles cases where the request URL has query
  // params or Vary headers that prevent an exact match against the precached entry.
  if (cacheKey) {
    const cachedByPath = await caches.match(cacheKey);
    if (cachedByPath) return cachedByPath;
  }

  const response = await fetch(request);
  const cache = await caches.open(SW_VERSION);
  // Clone before caching: Response body is a one-time-read stream; the original goes to the browser.
  cache.put(request, response.clone());
  return response;
}

// Network-first keeps online reloads fresh. Offline fallback returns only the
// cached app shell, not route-specific HTML generated by the SW.
async function navigationNetworkFirst(request) {
  try {
    return await fetch(request);
  } catch (error) {
    // Only catches network failures, not 4xx/5xx — those should reach the app.
    const cachedShell = await caches.match(APP_SHELL_URL);
    if (cachedShell) return cachedShell;
    throw error;
  }
}

// Runtime metadata uses network-first + cached fallback: fresh when online,
// usable when offline. Do not use this for API responses that app logic should
// observe as failures.
async function networkFirstCached(request, cacheKey) {
  try {
    const response = await fetch(request);
    const cache = await caches.open(SW_VERSION);
    // Refresh the cache on every successful fetch so the offline copy stays warm.
    cache.put(cacheKey || request, response.clone());
    return response;
  } catch (error) {
    const cached = await caches.match(cacheKey || request);
    if (cached) return cached;
    throw error;
  }
}

self.addEventListener("fetch", event => {
  const { request } = event;
  const url = new URL(request.url);
  // Use pathname (not full URL) for PRECACHE_SET lookup so query params don't cause misses.
  const path = url.pathname;

  if (appNavigation(request, url)) {
    event.respondWith(navigationNetworkFirst(request));
  } else if (dictionaryManifestRequest(request, url)) {
    event.respondWith(networkFirstCached(request, DICTIONARY_MANIFEST_URL));
  } else if (request.method === "GET" && sameOrigin(url) && PRECACHE_SET.has(path)) {
    // Pass path as cacheKey so cacheFirst can match even if request URL differs.
    event.respondWith(cacheFirst(request, path));
  }
});
