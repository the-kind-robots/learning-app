## Context

5e22fde (GH-408, ADR-0014) let a page ask a waiting worker to skip waiting, and made every page that started controlled reload on `controllerchange`. With DevTools' «Update on reload» on, every load of a controlled page installs the build again and activates it at once. Each activation reloaded every open page, and each reload installed the build again. The loop needs only one tab. The bisect is on #515. Separately, two builds running at once can write the local databases under different data models.

## Goals / Non-Goals

**Goals:**
- Never two builds at once in a release build.
- No reload loop, whatever DevTools flags are set.
- A development build can still take a recompile into a tab that stays open.

**Non-Goals:**
- Updating an app whose windows never all close. The cost is accepted on #515.
- Moving the other open development tabs onto a build that one tab took. They keep their code until they reload.

## Decisions

**The browser's lifecycle decides activation.** A waiting worker activates when no client of the old worker is left. That is the only point at which no page runs the old code, so nothing needs to reload and no page can mix builds. Considered instead:
- *A reload on controller change, limited to a change of build* (asking each worker its build). This fixes the loop, but two builds still run during the takeover.
- *Keep «Обновить» and reload every tab.* A tab in the middle of a write is reloaded under it, and the first tab runs the new build while the others are still loading the old one.

**The activation message stays in `sw.js`, and only a development bundle sends it.** `sw.js` is one file for every build, and the server cannot tell which bundle a page runs. The sender, `:effect/get-newest-build`, is registered under `goog/DEBUG`, so a release bundle carries no code that posts the message. Considered: having the backend omit the handler for a packaged run. That ties the worker to how the server runs rather than to the bundle, and a source checkout can serve a release bundle.

**The build mark reloads after activation.** It no longer relies on a reload on controller change. It waits for the taken worker to report `activated`, then reloads its own tab. In #515 measurements, reloading while the worker was still `activating` hung the navigation in 2 runs of 4.

## Risks / Trade-offs

- [An app never fully closed keeps its old build] → accepted (#515). The check on return to the foreground still installs the new build, so it is ready the moment the windows close.
- [DevTools' «Update on reload» activates a new build under open pages] → a developer tool. Pages keep their code and are not reloaded, so there is no loop.

## Migration Plan

The first deploy ships under the old worker, so pages still show «Обновить» and a tap takes the new build, as before. From then on the new build waits for every window to close. Rollback is a redeploy, with the same waiting.
