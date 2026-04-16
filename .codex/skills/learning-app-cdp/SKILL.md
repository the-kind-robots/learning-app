---
name: learning-app-cdp
description: Inspect or debug the learning app in a real browser through the project-specific Windows Chrome CDP workflow. Prefer this skill for browser testing, browser debugging, console inspection, network capture, DOM/runtime inspection, and service-worker-aware refreshes against https://sprecha.local or https://sprecha.de.
---

# Learning App CDP

Use this skill as the preferred browser-debugging workflow for this repository whenever the task involves the learning app in a real browser, especially while a human interacts with the page.

This is the project-specific layer on top of `windows-chrome-cdp`.

This skill is Windows-specific:

- it launches or reuses Windows `chrome.exe`
- it reaches DevTools HTTP endpoints through Windows `curl.exe`
- it sends CDP websocket messages through Windows `powershell.exe`

## When to use

Use this skill for:

- browser debugging or browser testing of the learning app in this repo
- watching console and network events while the user clicks through `sprecha.local` or `sprecha.de`
- opening the app in Windows Chrome with a remote debugging port
- inspecting the current page target, DOM state, or JS state on home, lesson, or vocabulary screens
- comparing local and production behavior in the same CDP workflow
- refreshing the app with service-worker update-on-reload enabled so local debugging does not inspect stale builds

Use this skill especially when the user cares about visual quality questions such as:

- whether a panel jumps
- whether an element is really centered
- whether a footer or prompt is visually stable during swaps
- whether an empty state animates or reflows incorrectly

In those cases, do not stop at DOM inspection alone. Prefer browser-level evidence such as:

- frame-by-frame `getBoundingClientRect()` capture across the transition
- DevTools animation/performance/layout traces
- buffered monitor events combined with measured element geometry
- screenshots or screencasts tied to specific transition phases

Do not use this skill for:

- generic browsing for unrelated sites
- Linux browsers
- projects that already manage the browser with Playwright or Puppeteer

## Approval and execution

In this environment, Windows executables invoked from WSL are most reliable when the shell command is run with escalated permissions.

When running scripts from this skill, prefer `exec_command` with:

- `sandbox_permissions: "require_escalated"`
- a short justification mentioning Windows Chrome or CDP

## Files

- `scripts/learning_app_cdp.sh` - repo-specific wrapper for local and production app targets
- `scripts/layout_probe.js` - reusable HTMX/layout geometry probe for swap transitions
- `scripts/setup_single_word.js` - reset vocab and create one-word scenario for delete-layout checks
- `scripts/run_lesson_next_enter_check.js` - prove `Enter` advances from `ДАЛЕЕ` without mouse
- `scripts/run_lesson_finish_enter_check.js` - prove `Enter` activates `ЗАКОНЧИТЬ` without mouse
- `scripts/run_lesson_finish_space_check.js` - prove `Space` activates `ЗАКОНЧИТЬ` without mouse
- `scripts/run_delete_last_word.js` - open edit mode and delete the only visible word
- `scripts/setup_filter_delete_scenario.js` - reset vocab and create two-word scenario for filter/delete bugs
- `scripts/run_filtered_delete_check.js` - apply a filter, delete the remaining visible word, and report the resulting empty state
- `/home/u473t8/.codex/skills/windows-chrome-cdp/scripts/chrome_cdp.sh` - shared global low-level CDP entrypoint used by the wrapper

## Quick workflow

1. Start or reuse local app in Windows Chrome:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh start-local
```

2. Start a buffered monitor on the current local app tab:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh monitor-local --session app-local
```

3. Let the user interact manually in Chrome.

4. Read buffered events:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh read --session app-local --tail 80
```

5. Stop the monitor when done:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh stop --session app-local
```

## Common patterns

Open the local app:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh start-local
```

Open the local lesson page directly:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh start-local --path /lesson
```

Open production:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh start-prod
```

Refresh local with service-worker update-on-reload:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh refresh-local
```

Wait until the local page reaches a known route or selector after navigation/setup:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh wait-local \
  --path /lesson \
  --selector '#lesson-answer'
```

Refresh production with service-worker update-on-reload:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh refresh-prod
```

Evaluate JavaScript in the local app page context:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --expression '({url: location.href, title: document.title})'
```

For dependent browser steps, do not run `setup`/`navigation` and the next `eval` in parallel.
After a setup script or route-changing action, prefer `wait-local` / `wait-prod` before the next `eval-*` call.

Evaluate a multiline script from a file:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file /tmp/inspect-idb.js
```

Use the reusable probes saved with this skill:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/layout_probe.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/setup_single_word.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/run_delete_last_word.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/run_lesson_next_enter_check.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/run_lesson_finish_enter_check.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/run_lesson_finish_space_check.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/setup_filter_delete_scenario.js
```

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file .codex/skills/learning-app-cdp/scripts/run_filtered_delete_check.js
```

Monitor local:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh monitor-local --session home-local
```

Monitor production:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh monitor-prod --session home-prod
```

Read recent monitor events:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh read --session home-local --tail 30
```

Capture warnings and errors after reload on production:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh console-prod --seconds 5
```

Read DOM state from the current local page:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --expression 'document.documentElement.outerHTML'
```

Read app state from the current page:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --expression '({url:location.href,title:document.title,app:document.querySelector(`#app`)?.innerText})'
```

List IndexedDB databases from the real app page:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --expression '(async () => indexedDB.databases())()'
```

Inspect PouchDB-backed IndexedDB internals with a custom script:

```bash
cat >/tmp/inspect-device-db.js <<'"'"'EOF'"'"'
(async () => {
  const db = await new Promise((resolve, reject) => {
    const request = indexedDB.open("device-db");
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
  return {
    name: db.name,
    version: db.version,
    stores: Array.from(db.objectStoreNames),
  };
})()
EOF

bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --file /tmp/inspect-device-db.js
```

Call whatever JS-visible function the DevTools console can see:

```bash
bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh eval-local \
  --expression 'Object.keys(window).filter(k => /app|cljs|shadow/i.test(k)).slice(0, 50)'
```

## Notes

- Local URL is `https://sprecha.local`.
- Production URL is `https://sprecha.de`.
- The wrapper defaults to port `9333`.
- For this repository, prefer this skill over generic browser workflows when inspecting app behavior in a real browser.
- `refresh-local` / `refresh-prod` enable Chrome's service-worker update-on-reload behavior before reloading the page.
- Use `monitor-start -> user interacts -> read` as the primary debugging workflow.
- For layout-stability bugs, combine event traces with visual measurements. HTMX/DOM traces can prove swap order, but they do not by themselves prove the absence of visual jerk.
- When a transition replaces the page or destroys the JS runtime, prefer an external DevTools trace or repeated geometry snapshots that survive the swap, rather than relying only on in-page probes.
- If DevTools is open, there may be multiple `page` targets; the wrapper filters by `sprecha.local` or `sprecha.de` so it attaches to the real app tab.
- Prefer `eval-local` / `eval-prod` for anything you would normally type into DevTools Console, including IndexedDB inspection and JS-visible app hooks.
- ClojureScript functions can be called only if they are visible in the page runtime. The wrapper does not invent exports; it gives you the same execution surface as DevTools Console.
- For deep inspection or custom CDP methods beyond the project wrapper, drop down to `/home/u473t8/.codex/skills/windows-chrome-cdp/scripts/chrome_cdp.sh`.
