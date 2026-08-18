const { test, expect } = require('@playwright/test');

// The phone half of the add-form stability specs (GH-289), rewritten for the
// list in flow (GH-373). Runs in the `mobile` project only: at 390x844 the
// autocomplete media query applies, and the suggestion list opens in the page
// flow under the value field instead of overlaying it.
//
// What this spec guards is no longer "nothing moves". In flow, what is below
// the list does move, by design. It guards that the box is exactly as tall as
// its rows — the defect in #373 was a fixed 176 px height that held at one row
// as at ten — that what it pushes down it pushes by its own height and no
// further than the ceiling, and that the field being typed into holds still.
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

// The phone ceiling from the stylesheet, `max-height: min(176px, 34svh)`; at
// 844 px tall, 34svh is 287 px, so 176 is the binding one. In flow this is not
// decoration: it is the furthest the form below the list can ever be pushed,
// which is why the spec knows the number.
const CEILING_PX = 176;

// `.suggestions { margin: 4px 0 0 }` — the gap between the field and the list,
// which travels with it.
const LIST_MARGIN_PX = 4;

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

// Nothing animates the list today — the height transition that #289 measured
// is not in master, so the box reaches its final size in one frame. Two frames
// is therefore a render sync rather than a wait for an animation, and the
// `getAnimations()` half stays as a guard: if a transition is ever added back,
// this settles on it instead of racing it. A cancelled transition rejects —
// that is a re-render retargeting the same height, not a failure.
const settleSuggestions = (page) =>
  page.locator('.suggestions').evaluate(async (list) => {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    await Promise.all(list.getAnimations().map((a) => a.finished.catch(() => {})));
  });

// PerformanceObserver hands entries over in a task queued after the frame that
// produced them, so a read taken in the same turn as the last assertion can
// miss the last shift and quietly pass. Two frames plus a task boundary is a
// pipeline sync, not a sleep.
const flushObservers = (page) =>
  page.evaluate(
    () =>
      new Promise((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(resolve, 0)));
      })
  );

test('the suggestion list fits its rows and pushes the form no further', async ({ page }) => {
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

  // The one class locator in the file, and it is not a shortcut around a role:
  // the list is a plain `ul` whose items carry `role="option"`, and what is
  // measured here is the container's own box against its own scrollHeight —
  // the rendered height versus the height the rows want. There is no
  // accessible handle for a box.
  const list = page.locator('.suggestions');
  const listBox = await list.boundingBox();
  // `clientHeight` and `scrollHeight` are both padding-box measurements, so
  // they compare like with like; `boundingBox().height` is the border box and
  // runs 2 px larger on this 1 px border, which is why the emptiness check
  // below does not use it.
  const listInner = await list.evaluate((el) => ({
    client: el.clientHeight,
    content: el.scrollHeight,
  }));
  const submitPush = submitAfter - restingSubmitY;

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
      submitPush: Math.round(submitPush),
      listHeight: Math.round(listBox.height),
      listClientHeight: listInner.client,
      listContentHeight: listInner.content,
      shiftScore: Number(shift.score.toFixed(4)),
      shiftExcludedByCls: Number(shift['input-excluded'].toFixed(4)),
      shiftEntries: shift.entries.length,
      shiftValues: shift.entries.map((e) => Number(e.value.toFixed(4))),
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

  // #373's acceptance, as a number: two rows occupy two rows. A box with a
  // fixed height fails this the moment the list is shorter than the height —
  // it renders 176 px around 89 px of rows.
  expect(listInner.client).toBe(listInner.content);
  expect(listBox.height).toBeLessThan(CEILING_PX);

  // What the list displaces, it displaces by exactly itself: the equality says
  // the push is the list plus its margin and nothing else has joined in, and
  // the translation field and the submit button travel together.
  expect(Math.round(submitPush)).toBe(Math.round(listBox.height) + LIST_MARGIN_PX);
  expect(Math.round(translationAfter.y - translationBefore.y)).toBe(Math.round(submitPush));

  // And it is bounded by the ceiling, so the worst case a long list can
  // produce is no worse than the fixed height this replaces (180 px).
  expect(submitPush).toBeLessThanOrEqual(CEILING_PX + LIST_MARGIN_PX);

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

  // Last on purpose, so everything above is evaluated and reported even while
  // this one is red — it is the open question of #373, not a flake.
  //
  // The unfiltered score is the only shift number that can see typing at all.
  // Measured on this change: 0.0566 over three entries, [0.0416, 0.0008,
  // 0.0142], stable across runs. The first is the list opening to its ceiling
  // and it is *the same shift the phone already produces today* — the fixed
  // height scored 0.0416 in one entry. The other two are the list narrowing
  // from six matches to two as the word is typed, which is precisely the
  // behaviour this issue asks for: a box that shrinks moves the page every
  // time it shrinks.
  //
  // The 0.05 budget comes from #289, where the in-flow list scored 0.028 — and
  // it did so with a 180 ms height transition spreading each move over a dozen
  // frames. Master has no such transition, so the movement lands in one frame
  // and costs more. The budget is kept at the number it was set to: whether
  // flow is worth 0.0566, or worth reintroducing the animation for, is a
  // decision to take against this measurement rather than around it.
  expect(shift.score).toBeLessThan(0.05);
});
