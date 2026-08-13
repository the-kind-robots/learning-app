const { test, expect } = require('@playwright/test');

// The phone half of the add-form stability specs (GH-289), phase 2 of #177.
// Runs in the `mobile` project only: at 390x844 the autocomplete media query
// applies, the suggestion list leaves its desktop overlay and opens in flow
// under the value field, pushing everything below it down the page.
//
// Two things had to change before this could be asserted at all:
//   - `__metrics().layoutShift` follows the CLS rule and drops entries with
//     hadRecentInput. Typing *is* recent input, so the shifts this spec is
//     about never reached it — hence the unfiltered `layoutShiftAll`.
//   - The suite had no viewport, so no phone media query had ever applied.
//
// The dictionary is the fixture the backend serves from
// LEARNING_APP__DICTIONARY_DIR (see README.md), not the shipped 37 MB file:
// every test gets a fresh context, so the worker imports the whole database
// into an empty OPFS once per test.

// Every prefix of this word matches the fixture, so the list opens on the
// first keystroke and never collapses mid-word — one clean open to measure.
const WORD = 'Fenster';
const WORD_MATCHES = 2; // das Fenster, die Fensterbank — the shipped dictionary gives more

// A different lemma for the warm-up: typing the target here would spend the
// very shift the spec exists to measure.
const PROBE = 'Haus';

const valueField = (page) => page.getByLabel('Слово (немецкий)');
const submitButton = (page) => page.getByRole('button', { name: 'ДОБАВИТЬ' });
const options = (page) => page.getByRole('option');

// The visually hidden legend inside the fieldset carries the same words as
// the panel title, so only the role tells them apart.
const panelTitle = (page) => page.getByRole('heading', { name: 'Добавить слово' });

const topOf = async (locator) => (await locator.boundingBox()).y;

// The list animates its height over 180 ms, so shifts arrive spread over a
// dozen frames and the last lands well after the final render. Waiting on the
// transitions themselves is exact where a timeout would be a guess: two frames
// for a just-started transition to register, then the browser's own completion
// promises. A cancelled transition rejects — that is a re-render retargeting
// the same height, not a failure.
const settleSuggestions = (page) =>
  page.locator('.suggestions').evaluate(async (list) => {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    await Promise.all(list.getAnimations().map((a) => a.finished.catch(() => {})));
  });

// PerformanceObserver hands entries over in a task queued after the frame that
// produced them, so a read taken in the same turn as the last assertion can
// miss the tail of the animation and quietly pass. Two frames plus a task
// boundary is a pipeline sync, not a sleep.
const flushObservers = (page) =>
  page.evaluate(
    () =>
      new Promise((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(resolve, 0)));
      })
  );

test('typing a word does not move the form under the cursor', async ({ page }) => {
  await page.goto('/home');

  const field = valueField(page);

  // The system starts asynchronously — PouchDB and the SQLite worker come up
  // before the render component does, and `install!` rides along with it. The
  // form being on screen is what says that component started.
  await expect(field).toBeVisible();

  // A release build eliminates the instrumentation; say so plainly rather than
  // failing later on undefined.
  expect(await page.evaluate(() => typeof window.__metrics)).toBe('function');

  const restingY = await topOf(submitButton(page));

  // The dictionary lives in a Worker that fetches and imports SQLite, and the
  // only honest signal that it finished is a suggestion appearing. Warm up on
  // another word, then wait for the list to fold back — the submit button
  // returning to its resting y is the end of the collapse — so the measured
  // window starts with the page genuinely at rest.
  //
  // Deliberately no reload in between: sqlite3-worker.js holds the
  // `sprecha-sqlite-opfs-sahpool` web lock with `ifAvailable` for the worker's
  // whole life, and a reload races the old worker's teardown against the new
  // worker's request. Losing that race answers "another tab open" and the
  // dictionary never becomes ready.
  await field.pressSequentially(PROBE);
  await expect(options(page).first()).toBeVisible();
  await field.fill('');
  await expect(options(page)).toHaveCount(0);
  await settleSuggestions(page);
  await expect.poll(() => topOf(submitButton(page))).toBeCloseTo(restingY, 1);

  const fieldBefore = await field.boundingBox();
  const titleBefore = await panelTitle(page).boundingBox();

  await page.evaluate(() => window.__metricsReset());

  // 120 ms per key is the measured human cadence (#195) and it is longer than
  // the 100 ms suggest debounce, so every keystroke gets its own dictionary
  // answer — the worst case for the render count, and the case the user
  // actually lives in.
  await field.pressSequentially(WORD, { delay: 120 });
  await expect(options(page)).toHaveCount(WORD_MATCHES);
  await settleSuggestions(page);
  await flushObservers(page);

  const fieldAfter = await field.boundingBox();
  const titleAfter = await panelTitle(page).boundingBox();
  const submitAfter = await topOf(submitButton(page));
  const scrolled = await page.evaluate(() => window.scrollY);
  const metrics = await page.evaluate(() => window.__metrics());

  // `clj->js` keeps the ClojureScript key names verbatim — `layout-shift-all`,
  // not `layoutShiftAll` — so these are bracket reads, not property reads.
  const shiftAll = metrics['layout-shift-all'];
  const shiftCls = metrics['layout-shift'];
  const longTasks = metrics['long-tasks'];
  const slowInteractions = metrics['slow-interactions'];

  // The numbers this spec exists to produce. The list reporter prints them on
  // every run, which is how the in-flow-versus-overlay question (#289) gets
  // answered with measurements instead of opinions.
  console.log(
    '[add-form mobile]',
    JSON.stringify({
      submitPush: Math.round(submitAfter - restingY),
      shiftAll: Number(shiftAll.toFixed(4)),
      shiftCls: Number(shiftCls.toFixed(4)),
      renders: metrics.renders,
      keystrokes: WORD.length,
      longTasks,
      slowInteractions,
      scrolled,
    })
  );

  // What the eye is asked to hold on to while typing: the field being typed
  // into, and the heading above it.
  expect(fieldAfter.y).toBe(fieldBefore.y);
  expect(titleAfter.y).toBe(titleBefore.y);

  // Everything below the list does move, and this is the budget for it.
  expect(shiftAll).toBeLessThan(0.05);

  // One render for the keystroke, one for the answer that follows it.
  expect(metrics.renders).toBeLessThanOrEqual(2 * WORD.length);

  // No keystroke took long enough to feel sluggish. This is the responsiveness
  // assertion, and `event` entries are the right instrument for it: they are
  // anchored to the user's own input, so they measure this form rather than
  // whatever else the machine is doing.
  //
  // `long-tasks` is logged but deliberately not asserted on. Its threshold is
  // 50 ms by definition, and an unminified dev build under WSL crosses that on
  // background work alone — measured three tasks of 58-81 ms with the browser
  // running by itself and nothing typed into the page since the reset. An
  // assertion that fails on the machine's mood rather than on the code is
  // worse than no assertion: it teaches people to rerun until green.
  //
  // This one holds because the mobile project waits for the desktop one and
  // then runs alone (see playwright.config.js). Run several copies of this
  // spec at once — `--repeat-each` without `--workers=1` — and it fails by
  // construction: keystrokes measured 128-336 ms with five browsers sharing
  // the machine. The geometry assertions above survive that; timing cannot.
  expect(slowInteractions).toEqual([]);
});
