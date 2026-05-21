## Why

The app is marketed and used as offline-first, but an already-loaded app shows the browser's built-in "You're offline" page when network is disabled and the page is reloaded. This breaks the basic PWA promise: cached app routes must reload into our app shell, not into the browser error screen.

## What Changes

- Make the Service Worker serve the cached app shell for same-origin navigation requests when the network cannot provide the page.
- Keep API requests and non-navigation resources out of the app-shell fallback path.
- Cache the small dictionary manifest with network-first semantics so an already-imported OPFS dictionary can boot offline.
- Keep the Service Worker passive: it may return cached static shell/assets, but it must not execute app logic, Ring handlers, or hiccup rendering.
- Add verification for offline navigation reload behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `main-thread-runtime`: clarify that passive Service Worker caching includes an offline navigation fallback to the cached app shell.

## Impact

- `resources/public/js/sw.js` fetch handling for navigation requests.
- Main-thread runtime/PWA spec and OpenSpec archive.
- Browser verification in real Chrome, because the bug is visible only through actual Service Worker control and offline reload behavior.
