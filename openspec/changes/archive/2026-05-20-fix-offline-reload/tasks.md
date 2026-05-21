## 1. Reproduce and Scope

- [x] 1.1 Confirm current offline reload failure with a controlled Service Worker/browser run.
- [x] 1.2 Identify the exact navigation/resource fetch paths that must and must not use app-shell fallback.

## 2. Service Worker Implementation

- [x] 2.1 Add navigation-only network-first fallback to cached `/` app shell.
- [x] 2.2 Preserve cache-first behavior for precached static assets.
- [x] 2.3 Ensure `/api/*` and non-navigation requests never receive HTML app-shell fallback.

## 3. Verification and Spec Closeout

- [x] 3.1 Run static/compile checks relevant to the changed client assets.
- [x] 3.2 Verify in a real browser that offline reload shows the app, not Chromium's offline page.
- [x] 3.3 Validate and archive the OpenSpec change before PR merge.
