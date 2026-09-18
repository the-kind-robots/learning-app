## Context

`sw.js` never calls `skipWaiting`: the activate handler deletes every cache bucket but its own, so a worker that activated under a live tab would pull that tab's assets from under it (#278). The cost is that a new build waits until every page of the origin is gone, and on Android a swiped-away PWA is not gone. `main.cljs` registers the worker in `:worker/service-worker` and does nothing else with the registration.

## Goals / Non-Goals

**Goals:**
- Activate a waiting worker on the user's request without any page running on a deleted bucket.
- Notice a new build when the phone returns to the app.

**Non-Goals:**
- `skipWaiting` on install, or any activation the user did not ask for, in any build.
- Changing what the worker caches or how it serves.

## Decisions

- **Activation is a message, not a lifecycle default.** The worker listens for `{type: "activate-waiting"}` and calls `skipWaiting` then. The page decides when; the worker keeps #278's default. Alternative — `skipWaiting` in install — rejected for the reason #278 records.
- **Every page reloads itself once on `controllerchange`.** This is what keeps #278's reasoning intact: the bucket is deleted, but no page keeps running on it, because each one reloads under the new controller. Only a page that started controlled reloads — the first worker claiming a fresh page fires the same event and must not reload it. A flag guards the double reload.
- **Detection covers both moments.** `registration.waiting` at registration (the build changed while the app was closed) and `updatefound` → installing worker → state `installed` while a controller exists (the build changed while the app was open). Both feed one offer function.
- **The offer is the same in every build.** It saves `:pwa/update-waiting?` and the presenter turns it into `:show-update?` for an «Обновить» button in the shell actions row. Activating automatically in a development build was the alternative and is wrong: the dev stand's watch writes into `resources/public`, the backend derives `SW_VERSION` from a hash of it, so every recompile is a new worker and every recompile would reload every open page, losing shadow-cljs's hot reload. The developer takes a new build the same way.
- **Update on visibility.** `registration.update()` on `visibilitychange` to visible, since a PWA coming back from the background makes no navigation.
- **The trace export is a shell action.** It was the red D's click; a development build's control belongs with the shell's other controls, and the D goes back to being the last letter of the word mark — no handler, no name of its own.
- **The service worker code lives in its own client namespace** (`service-worker`), started by `main.cljs` after `:app/render` so it can dispatch the state flag; the shell's registration stays passive otherwise.

## Risks / Trade-offs

- [A reload mid-lesson] → a page only reloads after the user tapped «Обновить»; nothing reloads unasked, in any build.
- [Two tabs reloading at once] → both reload under the new controller; each page reloads once, and the reload is a normal navigation the worker answers network-first.
- [The browser spec runs on a development build] → the control is the same in both builds, so the spec proves the offered path whichever build it runs against.
