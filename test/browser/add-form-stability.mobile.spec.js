const { test, expect } = require('@playwright/test');

// The phone half of the add-form stability specs (GH-289), phase 2 of #177.
// Runs in the `mobile` project only: at 390x844 the autocomplete media query
// applies, and before this change the suggestion list left its desktop overlay
// to open in flow under the value field, pushing everything below it down.
//
// Why this spec does not assert on CLS, measured rather than assumed. CLS is
// defined to ignore any layout-shift entry with `hadRecentInput`, which the
// browser sets on every entry within 500 ms of a keystroke. Measured on the
// in-flow layout: 11 entries, 0.028 total, `hadRecentInput` true on all
// eleven, so CLS read 0.000. The same page, given a shift with no input near
// it, reported 0.224 — the instrument works, it is the definition that looks
// away. A software keyboard is invisible to it twice over: shrinking the
// viewport from 844 to 520 emitted no layout-shift entries at all.
//
// So the numbers this spec trusts are the geometry — `boundingBox()` before
// and after — and the unfiltered `layout-shift.score`. `web-vitals.cls` is
// logged beside them to keep the contrast visible, never asserted on.
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

// INP's own "good" boundary. Not a budget invented here.
const INP_BUDGET_MS = 200;

const valueField = (page) => page.getByLabel('Слово (немецкий)');
const translationField = (page) => page.getByLabel('Перевод (русский)');
const submitButton = (page) => page.getByRole('button', { name: 'ДОБАВИТЬ' });
const options = (page) => page.getByRole('option');

// The visually hidden legend inside the fieldset carries the same words as
// the panel title, so only the role tells them apart.
const panelTitle = (page) => page.getByRole('heading', { name: 'Добавить слово' });

const topOf = async (locator) => (await locator.boundingBox()).y;

// INP as the web-vitals library reports it, once it has reported anything.
// Null until the library has seen an interaction worth naming.
const interactionLatency = async (page) => {
  await expect
    .poll(() => page.evaluate(() => window.__metrics()['web-vitals'].inp?.value ?? null),
          { timeout: 10000 })
    .not.toBeNull();
  return page.evaluate(() => window.__metrics()['web-vitals'].inp.value);
};

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

  const restingSubmitY = await topOf(submitButton(page));

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
  await expect.poll(() => topOf(submitButton(page))).toBeCloseTo(restingSubmitY, 1);

  const fieldBefore = await field.boundingBox();
  const titleBefore = await panelTitle(page).boundingBox();
  const translationBefore = await translationField(page).boundingBox();

  // INP is the library's own accumulating state: `__metricsReset()` clears our
  // copy of it but cannot rewind the library, so after the reset it re-reports
  // only if a *new* worst interaction appears. Keeping the warm-up's reading
  // means the final worst is whichever of the two is larger, which is exactly
  // the number "was any interaction slow" wants.
  const inpBeforeReset = await interactionLatency(page);

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
  const translationAfter = await translationField(page).boundingBox();
  const submitAfter = await topOf(submitButton(page));
  const scrolled = await page.evaluate(() => window.scrollY);

  // `clj->js` keeps the ClojureScript key names verbatim — `layout-shift`,
  // not `layoutShift` — so these are bracket reads, not property reads.
  const metrics = await page.evaluate(() => window.__metrics());
  const shift = metrics['layout-shift'];
  const vitals = metrics['web-vitals'];
  const longFrames = metrics['long-frames'];

  // The numbers this spec exists to produce. The list reporter prints them on
  // every run, which is how the in-flow-versus-overlay question (#289) gets
  // answered with measurements instead of opinions. `cls` sits next to
  // `shiftScore` on purpose: the gap between them is the whole reason the
  // assertions below read geometry.
  console.log(
    '[add-form mobile]',
    JSON.stringify({
      submitPush: Math.round(submitAfter - restingSubmitY),
      shiftScore: Number(shift.score.toFixed(4)),
      shiftExcludedByCls: Number(shift['input-excluded'].toFixed(4)),
      shiftEntries: shift.entries.length,
      cls: vitals.cls ? Number(vitals.cls.value.toFixed(4)) : null,
      inp: vitals.inp ? vitals.inp.value : inpBeforeReset,
      renders: metrics.renders,
      keystrokes: WORD.length,
      longFrames: longFrames.length,
      scrolled,
    })
  );

  // What the eye is asked to hold on to while typing: the field being typed
  // into, and the heading above it.
  expect(fieldAfter.y).toBe(fieldBefore.y);
  expect(titleAfter.y).toBe(titleBefore.y);

  // And what the in-flow list used to push down the page. These are the
  // assertions that actually fail when the list re-enters the flow: it moved
  // both of these by 184 px, while CLS reported 0.000 throughout.
  expect(translationAfter.y).toBe(translationBefore.y);
  expect(submitAfter).toBe(restingSubmitY);

  // The unfiltered score, which is the only shift number that can see typing
  // at all. Measured 0.000 as an overlay, 0.028 in flow.
  expect(shift.score).toBeLessThan(0.05);

  // Every entry recorded here is one CLS discarded, so a nonzero score with a
  // zero `input-excluded` would mean something shifted outside the typing —
  // a different defect, and worth knowing about.
  expect(shift['input-excluded']).toBeCloseTo(shift.score, 4);

  // One render for the keystroke, one for the answer that follows it.
  expect(metrics.renders).toBeLessThanOrEqual(2 * WORD.length);

  // No keystroke took long enough to feel sluggish. INP is the standard
  // instrument for that and it is anchored to the user's own input, so it
  // measures this form rather than whatever else the machine is doing.
  // Measured 48-56 ms here, against INP's own 200 ms "good" boundary.
  //
  // `long-frames` is logged but deliberately not asserted on. A long frame is
  // main-thread time, and an unminified development build under WSL produces
  // them on background work alone. An assertion that fails on the machine's
  // mood rather than on the code is worse than no assertion: it teaches people
  // to rerun until green.
  //
  // Even INP holds only because the mobile project waits for the desktop one
  // and then runs alone (see playwright.config.js). Run several copies at once
  // — `--repeat-each` without `--workers=1` — and it fails by construction:
  // keystrokes measured 128-336 ms with five browsers sharing the machine. The
  // geometry assertions above survive that; timing cannot.
  // A plain read, not a poll: after the reset an unchanged worst interaction
  // is reported by nobody, and that is a pass, not a timeout.
  const inpAfter = await page.evaluate(
    () => window.__metrics()['web-vitals'].inp?.value ?? null
  );
  const inp = Math.max(inpBeforeReset ?? 0, inpAfter ?? 0);
  expect(inp).toBeGreaterThan(0); // an unreported INP would make the next line vacuous
  expect(inp).toBeLessThan(INP_BUDGET_MS);
});
