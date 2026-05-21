## Context

The current Service Worker precaches `/` and static assets, but its fetch handler only responds to exact precache URL matches. A browser reload of `/home`, `/words`, or `/lesson` while offline is a navigation request for a route that is not in `PRECACHE_URLS`, so the request falls through to the network and Chromium shows its built-in offline page.

In-force ADRs keep app execution on the main thread and use the Service Worker only as a passive cache layer. This change keeps that boundary: the Service Worker may return the cached static app shell document, but it must not run route logic, render hiccup, touch app state, or call APIs.

## Goals / Non-Goals

**Goals:**

- Offline reload of same-origin app routes returns the cached app shell instead of the browser offline page.
- API calls and non-navigation resource failures do not receive the app shell as a fake response.
- The Service Worker remains a cache/fallback layer, not an application runtime.
- Browser verification proves actual Service Worker offline reload behavior.

**Non-Goals:**

- No new custom offline UI in this change.
- No background sync, data sync, or task retry behavior changes.
- No change to dictionary OPFS or PouchDB storage semantics.
- No duplicate Cache Storage copy of the large dictionary SQLite file.

## Decisions

1. **Navigation-only fallback to cached `/`.**
   - Decision: for same-origin `request.mode === "navigate"` requests outside `/api/`, try the network first; if it fails, return cached `/` from the precache.
   - Rationale: this preserves fresh online navigations while giving offline reloads a deterministic app shell.
   - Alternative considered: add every route to `PRECACHE_URLS`. Rejected because frontend routes are logical app states, not separate static documents.

2. **Keep exact cache-first behavior for precached assets.**
   - Decision: leave static asset handling cache-first for `PRECACHE_SET` paths.
   - Rationale: the current asset behavior is working and independent from navigation fallback.
   - Alternative considered: network-first all assets. Rejected as a larger caching policy change.

3. **Do not fallback API/non-navigation requests to HTML.**
   - Decision: only navigation requests can receive the cached app shell. `/api/` and worker/data requests either use their existing cache path or fail normally.
   - Rationale: returning HTML for data requests hides failures and makes adapters harder to reason about.

4. **Cache dictionary manifest as boot metadata.**
   - Decision: cache `/dictionary/manifest` with network-first semantics and cached fallback.
   - Rationale: an already-imported OPFS dictionary still needs the current manifest to find the content-addressed file while offline.
   - Alternative considered: precache the whole dictionary SQLite file in Cache Storage. Rejected because that would duplicate the large dictionary beside OPFS.

## Risks / Trade-offs

- [Risk] First visit offline still cannot load because nothing is cached yet. → Mitigation: acceptance is for an already-loaded app with an installed Service Worker.
- [Risk] A stale app shell can load after deploy while offline. → Mitigation: SW versioned cache already changes with deployed `SW_VERSION`; online visits refresh the cache.
- [Risk] Browser verification can be flaky if the old SW controls the page. → Mitigation: force service-worker update/unregister or use update-on-reload during validation, then reload once online before switching offline.

## Migration Plan

- Update `sw.js` fetch handling.
- Add focused tests/verification where feasible, plus browser smoke for real offline reload.
- Archive the OpenSpec change before PR merge.

## Open Questions

None.
