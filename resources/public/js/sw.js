// SW_VERSION and PRECACHE_URLS are prepended by the server:
//
//   const SW_VERSION="abcd1234";
//   const PRECACHE_URLS=["/","/css/styles.css", ...];
//
// SW_VERSION doubles as the cache bucket name. Each deploy gets a fresh bucket;
// the activate handler deletes every bucket whose name isn't SW_VERSION, so
// stale assets from old deployments are evicted automatically.
//
// PRECACHE_URLS is the static app shell, derived from the files under
// resources/public rather than typed out here: whole directories that hold
// shell assets and nothing else (css, fonts, icons), plus the few files named
// one by one in core.clj. Whatever else is in a checkout — an old build's
// output, a downloaded file — is not in it. These are safe to cache during SW
// install and serve cache-first because their lifecycle is tied to
// SW_VERSION. Keep dynamic/runtime metadata out of it; it needs its own cache
// policy — and a path in the list that no longer exists is worse than one
// missing, since cache.addAll is atomic and the install would never finish.

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

// No skipWaiting in install (#278), and none on request in a release build
// (ADR-0017): two builds must never run at once, since both would write to the
// same local databases under their own data models. A new worker waits until
// every window of the app is closed; the browser activates it then, and the
// next open runs it.
//
// The one exception is development. A development bundle's build mark sends
// "activate-waiting" so that a recompile reaches a tab that stays open, and the
// tab reloads itself once the new worker is activated (service_worker.cljs).
// This file is the same in every build and cannot tell which one sent the
// message; what keeps the handler out of reach of a release build is that the
// release bundle has no code that sends it.
self.addEventListener("message", event => {
  if (event.data && event.data.type === "activate-waiting") {
    self.skipWaiting();
  }
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== SW_VERSION).map(k => caches.delete(k))))
      // claim() takes control of the pages already open: the first page of a
      // first install, or the tab whose build mark asked in a development build.
      .then(() => self.clients.claim())
  );
});


// Only a same-origin success is worth keeping (#315): the bucket lives until
// the next deploy, so one cached 404 or 500 would be served on every later
// load. An opaque or error response goes to the page and nowhere else.
async function keep(key, response) {
  if (!(response.ok && response.type === "basic")) return;
  // Clone now, before the first await: a body is read once, and the original
  // goes to the page, which may start reading it while the cache opens.
  const copy = response.clone();
  const cache = await caches.open(SW_VERSION);
  await cache.put(key, copy);
}

// Keyed by path: a query string neither misses the precached entry nor adds
// one of its own. A hit carries the path it was stored under as its URL, not
// the request's, so nothing served from here may read its own query (#299).
async function cacheFirst(request, path) {
  const cached = await caches.match(path);
  if (cached) return cached;

  const response = await fetch(request);
  keep(path, response);
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
    // Refresh the offline copy on every successful fetch — and only then: a
    // failed answer must not replace the good copy.
    keep(cacheKey, response);
    return response;
  } catch (error) {
    const cached = await caches.match(cacheKey);
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
    event.respondWith(cacheFirst(request, path));
  }
});
