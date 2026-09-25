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

// No skipWaiting in install (#278): activating deletes every other bucket,
// and a page still open on the old build loads its assets from one of them.
// A new worker therefore waits until a page asks for it. Asking is safe
// because every page that started under a controller reloads itself once
// on controllerchange (service_worker.cljs, ADR-0014) — the old bucket goes,
// and no page keeps running on it. No build asks by itself: the request is
// always the user's — «Обновить» in every build, or a tap on the red D in a
// development build.
self.addEventListener("message", event => {
  if (event.data && event.data.type === "activate-waiting") {
    self.skipWaiting();
  }
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

// A cached response carries the URL it was stored under. Re-wrapped, it has
// none, and the browser gives it the request's — query included. A worker
// takes its response's URL as its own location, so without this
// `/js/sqlite3-worker.js?sqlite3.dir=/js&telemetry=1` lost its parameters
// under a controlled page (#299).
function underRequestUrl(cached) {
  return new Response(cached.body, cached);
}

// Keyed by path: a query string neither misses the precached entry nor adds
// one of its own.
async function cacheFirst(request, path) {
  const cached = await caches.match(path);
  if (cached) return underRequestUrl(cached);

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
