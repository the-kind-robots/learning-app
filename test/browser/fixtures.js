const base = require('@playwright/test');

// Every spec imports `test` and `expect` from here, not from
// '@playwright/test', so that each test also fails on a nested render
// (#515).
//
// A nested render happens when a life-cycle hook or an event handler writes
// the store while Replicant is rendering. Replicant does not run such a
// render. It keeps its hiccup and renders it in a later animation frame, and
// by then the hiccup may be an older screen than the one on display. A tab
// that is not on screen runs no frames, so the old screen is rendered when
// the tab is shown. A development build reports each nested render on the
// console as "Replicant warning: Triggered a render while rendering".
//
// The report itself is sent in an animation frame, and the deferred render
// that runs in a frame can report another one. So after the test every open
// page runs frames, all pages at once, and then the check waits a short quiet
// spell. A report that arrived meanwhile means another round, up to a few.
// A page that runs no frames (one left navigating, say) is given up on after
// a second, timed from here rather than inside the page, since a page that
// runs nothing runs no timer either.

const NESTED_RENDER = 'Triggered a render while rendering';
const PAGE_WAIT_MS = 1000;
const QUIET_MS = 250;
const ROUNDS = 4;

const nextFrames = () =>
  new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const test = base.test.extend({
  context: async ({ context }, use) => {
    const nested = [];
    const listen = (page) =>
      page.on('console', (message) => {
        if (message.text().includes(NESTED_RENDER)) nested.push(page.url());
      });
    context.pages().forEach(listen);
    context.on('page', listen);

    await use(context);

    for (let round = 0; round < ROUNDS; round++) {
      const before = nested.length;
      await Promise.all(context.pages().map((page) =>
        Promise.race([page.evaluate(nextFrames).catch(() => {}), pause(PAGE_WAIT_MS)])));
      await pause(QUIET_MS);
      if (nested.length === before) break;
    }
    base.expect(nested, `Replicant: ${NESTED_RENDER}`).toEqual([]);
  },
});

module.exports = { test, expect: base.expect, chromium: base.chromium };
