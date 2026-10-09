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
//
// A spec that delays frames (startup-splash.spec.js) declares the delay on
// the page as `__frameDelayMs`, and both waits stretch by it: two chained
// frames there take twice the delay, so against the plain one-second
// give-up and the short quiet spell the guard would stop before a report
// queued behind the delayed frames could run — silently inert in the one
// spec built around late frames.

const NESTED_RENDER = 'Triggered a render while rendering';
const PAGE_WAIT_MS = 1000;
const QUIET_MS = 250;
const ROUNDS = 4;

const nextFrames = () =>
  new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const frameDelay = (page) =>
  page.evaluate(() => window.__frameDelayMs || 0).catch(() => 0);

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
      const pages = context.pages();
      const delays = await Promise.all(pages.map(frameDelay));
      await Promise.all(pages.map((page, i) =>
        Promise.race([page.evaluate(nextFrames).catch(() => {}),
                      pause(PAGE_WAIT_MS + 2 * delays[i])])));
      await pause(QUIET_MS + Math.max(0, ...delays));
      if (nested.length === before) break;
    }
    base.expect(nested, `Replicant: ${NESTED_RENDER}`).toEqual([]);
  },
});

// Asserting that something never happens: auto-waiting can only wait for a
// thing to become true, so the negative is established by letting a
// reaction's worth of time pass first. The one sanctioned fixed wait.
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

module.exports = { test, expect: base.expect, chromium: base.chromium, nothingHappensFor };
