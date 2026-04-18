---
name: learning-app-remote-inspect
description: Step-by-step debugging workflow for the learning app through a phone's Chrome remote-inspect target. Use when the user has `chrome://inspect/#devices` open and wants a copy-paste command loop for Console/Elements/Network instead of direct automation.
---

# Learning App Remote Inspect

Use this skill when:

- the user already opened a phone tab through `chrome://inspect/#devices`
- the page is running on a real phone
- direct automation of the mirrored target is awkward or unavailable
- we want a human-in-the-loop loop: assistant gives one command, user pastes it, user returns a structured result

This skill is the manual companion to `learning-app-cdp`.

`learning-app-cdp` is still preferred when repo-owned CDP automation works.
Use this skill when we need the real phone/runtime but must drive it through the user's open DevTools window.

## Core Loop

Always work in small steps:

1. Ask the user to keep the correct phone tab open in remote inspect.
2. Give exactly one command at a time unless two commands are clearly independent.
3. Prefer Console snippets that return one compact object.
4. After each command, ask the user to reply in the exact result format below.
5. Use the returned data to decide the next command.

Do not ask the user to summarize what happened in prose if a snippet can measure it.

## Reply Format

When giving a command, always ask the user to answer in this format:

```text
COMMAND:
<short command label>

RESULT:
<paste the exact console output or the exact visible error text>

OBSERVED:
<one short sentence about what changed on the phone, if anything>
```

If the user only sends a screenshot or partial text, continue, but prefer getting back to this format on the next step.

## Command Style

Prefer snippets like:

- one self-invoking function
- one returned object
- no dependencies on repo-local scripts
- no giant logs unless we are tracing a timeline

Good shape:

```js
(() => {
  const row = document.querySelector(".word-item");
  return {
    path: location.pathname,
    hasRow: !!row,
    rowTop: row?.getBoundingClientRect().top ?? null
  };
})()
```

Avoid:

- long multi-purpose probes when a narrower probe will answer the question
- asking the user to browse Elements manually when a Console query can answer it
- asking the user to type commands by hand; always give copy-paste blocks

## Default Investigation Order

For UI/jank bugs, prefer this order:

1. Confirm route and active DOM nodes.
2. Capture geometry before interaction.
3. Trigger the interaction.
4. Capture geometry after interaction.
5. Only then inspect CSS/computed style/event timing.

For layout bugs, favor:

- `getBoundingClientRect()`
- `scrollTop`, `scrollHeight`, `clientHeight`
- `visualViewport`
- `document.activeElement`
- `getComputedStyle(...)`

For event-flow bugs, favor:

- temporary listeners on `htmx:beforeSwap`, `htmx:afterSwap`, `htmx:afterSettle`
- `focusin`, `focusout`
- returned buffered arrays on `window`

## Standard Probes

### 1. State Snapshot

```js
(() => ({
  path: location.pathname,
  title: document.title,
  activeTag: document.activeElement?.tagName ?? null,
  activeId: document.activeElement?.id ?? null,
  viewport: {
    innerWidth,
    innerHeight,
    visualWidth: visualViewport?.width ?? null,
    visualHeight: visualViewport?.height ?? null
  }
}))()
```

### 2. Geometry Snapshot

```js
(() => {
  const rect = (node) => {
    if (!node) return null;
    const r = node.getBoundingClientRect();
    return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, width: r.width, height: r.height };
  };

  return {
    list: rect(document.querySelector(".vocabulary__list")),
    footer: rect(document.querySelector(".vocabulary__footer")),
    editing: rect(document.querySelector(".word-item--editing")),
    input: rect(document.querySelector(".word-item--editing input"))
  };
})()
```

### 3. One-Shot Interaction Probe

```js
(async () => {
  const list = document.querySelector(".vocabulary__list");
  const row = document.querySelector(".word-item");
  const display = row?.querySelector(".word-item__display");
  if (!list || !display) return { error: "missing-list-or-row" };

  const before = {
    scrollTop: list.scrollTop,
    activeTag: document.activeElement?.tagName ?? null,
    rowTop: row.getBoundingClientRect().top
  };

  display.click();
  await new Promise((resolve) => setTimeout(resolve, 1200));

  const editing = document.querySelector(".word-item--editing");
  const input = editing?.querySelector("input");

  return {
    before,
    after: {
      scrollTop: list.scrollTop,
      activeTag: document.activeElement?.tagName ?? null,
      editingTop: editing?.getBoundingClientRect().top ?? null,
      inputTop: input?.getBoundingClientRect().top ?? null
    }
  };
})()
```

### 4. Temporary Event Timeline

```js
(() => {
  window.__probe = [];
  const push = (phase) => {
    window.__probe.push({
      phase,
      ts: performance.now(),
      activeTag: document.activeElement?.tagName ?? null,
      activeId: document.activeElement?.id ?? null
    });
  };

  push("armed");
  document.body.addEventListener("htmx:beforeSwap", () => push("beforeSwap"), { once: false });
  document.body.addEventListener("htmx:afterSwap", () => push("afterSwap"), { once: false });
  document.body.addEventListener("htmx:afterSettle", () => push("afterSettle"), { once: false });
  document.body.addEventListener("focusin", () => push("focusin"), { once: false });
  "armed";
})()
```

Then read it with:

```js
window.__probe
```

## How To Talk To The User

When using this skill:

- give one short sentence of context
- give one code block to paste
- ask for the exact result in the fixed format

Preferred pattern:

```text
Paste this into the remote DevTools Console on the phone tab:

```js
...
```

Reply like this:

COMMAND:
state-snapshot

RESULT:
...

OBSERVED:
...
```

## Decision Rule

If the user has both:

- a real phone remote-inspect session
- a repo-owned CDP path

then:

- use `learning-app-cdp` for anything we can automate directly
- use this skill when the real phone behavior is the source of truth and the user is willing to act as the hands

