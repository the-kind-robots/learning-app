const { test, expect } = require('@playwright/test');

// The dictionary belongs to the tab being typed into (GH-351).
//
// `opfs-sahpool` admits one holder, so a tab takes the pool lock when it comes
// to the foreground — on screen and holding the keyboard — and gives it back
// when it leaves. A tab waiting its turn has no dictionary and holds the
// queries asked of it until it does.
//
// How the foreground is driven here, and what that costs in confidence:
// Playwright can reproduce neither half. Every page reports `visible` and
// `hasFocus() === true`, `bringToFront()` changes neither, no `focus`/`blur`
// event fires from it, and `Emulation.setPageVisibilityOverride` is gone from
// the protocol — all checked against this Chrome, headless and headed. So
// `document.hidden`, `document.visibilityState` and `document.hasFocus` are
// shimmed and the real `visibilitychange`, `focus` and `blur` events are
// dispatched, which drives the app's own path end to end: page listener,
// worker message, lock, pool, database.
//
// What that does NOT reproduce: Android freezing a backgrounded tab, which is
// why the visibility half exists, and real OS focus, which is why the focus
// half does. Neither is covered by any test here.
//
// The dictionary is the fixture the backend serves from
// LEARNING_APP__DICTIONARY_DIR (see README.md); "Fenster" matching exactly two
// lemmas is what tells it apart from the shipped file.

const FIXTURE_FENSTER_MATCHES = 2;

// A tab taking its first turn loads the engine, installs the pool and opens
// the database — 94 ms measured on this machine. Later turns cost ~14 ms.
const TURN_MS = 20000;

// Long enough that a dictionary that was going to answer would have, but not
// so long that the spec pays for it when the answer never comes.
const SILENCE_MS = 3000;

const valueField = (page) => page.getByLabel('Слово (немецкий)');
const options = (page) => page.getByRole('option');

const PAGE_STATE_SHIM = ({ hidden, focused }) => {
  let isHidden = hidden;
  let isFocused = focused;
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => isHidden });
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => (isHidden ? 'hidden' : 'visible'),
  });
  document.hasFocus = () => isFocused;
  window.__setHidden = (value) => {
    isHidden = value;
    document.dispatchEvent(new Event('visibilitychange'));
  };
  window.__setFocused = (value) => {
    isFocused = value;
    window.dispatchEvent(new Event(value ? 'focus' : 'blur'));
  };
};

// A new tab opens in front, which in a real browser takes the keyboard off
// whichever tab had it. Nothing here knows about the other pages, so callers
// that care say so with `focusOnly`.
const openHome = async (context, { hidden = false, focused = true } = {}) => {
  const page = await context.newPage();
  await page.addInitScript(PAGE_STATE_SHIM, { hidden, focused });
  await page.goto('/home');
  await expect(valueField(page)).toBeVisible();
  return page;
};

const show = (page) => page.evaluate(() => window.__setHidden(false));
const hide = (page) => page.evaluate(() => window.__setHidden(true));
const blur = (page) => page.evaluate(() => window.__setFocused(false));
const focus = (page) => page.evaluate(() => window.__setFocused(true));

// One tab has the keyboard at a time. Blurring the others first is the order a
// real browser uses, and it is the order that keeps the lock uncontended.
const focusOnly = async (page, ...others) => {
  for (const other of others) await blur(other);
  await focus(page);
};

const type = async (page, word) => {
  await valueField(page).fill('');
  await valueField(page).fill(word);
};

// Matching on the text, so a list left over from a previous word cannot pass.
const suggestions = (page, word) => options(page).filter({ hasText: word });

// A plain wait, named so the reason is visible at the call site: these
// assertions are that nothing appears, and nothing appearing takes time to
// establish.
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

test('the foreground tab has the dictionary and a background one does not', async ({ context }) => {
  const first = await openHome(context);
  await type(first, 'Fenster');
  await expect(suggestions(first, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });

  // The second tab opens in front, so the first steps aside and the second
  // takes over. Then the keyboard goes back, and the second is left visible
  // but not in front — which is where a tab has no dictionary.
  const second = await openHome(context);
  await focusOnly(first, second);

  await type(second, 'Fenster');
  await nothingHappensFor(second, SILENCE_MS);
  await expect(options(second)).toHaveCount(0);
});

test('two visible tabs: the one with the keyboard gets the dictionary', async ({ context }) => {
  // Neither tab is hidden at any point here — this is the split-screen case,
  // where `document.hidden` is false for both and cannot decide between them.
  const left = await openHome(context);
  const right = await openHome(context);

  await focusOnly(left, right);
  await type(left, 'Fenster');
  await expect(suggestions(left, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
  await type(right, 'Frage');
  await nothingHappensFor(right, SILENCE_MS);
  await expect(options(right)).toHaveCount(0);

  // The user clicks into the other pane. Nothing changed about what is on
  // screen; the dictionary still has to move.
  await focusOnly(right, left);
  await expect(suggestions(right, 'Frage')).toHaveCount(1, { timeout: TURN_MS });
});

test('the query asked while waiting is answered when the turn comes', async ({ context }) => {
  const first = await openHome(context);
  await type(first, 'Fenster');
  await expect(suggestions(first, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });

  const second = await openHome(context);
  await focusOnly(first, second);
  await type(second, 'Fenster');
  await expect(options(second)).toHaveCount(0);

  // No further typing after this point. The query the second tab already sent
  // has to survive the wait and be answered once it has a database.
  await focusOnly(second, first);

  await expect(suggestions(second, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
});

test('the tab that leaves the foreground gives the dictionary back', async ({ context }) => {
  const first = await openHome(context);
  await type(first, 'Fenster');
  await expect(suggestions(first, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });

  const second = await openHome(context);

  // Backgrounded, not merely blurred: both halves of the rule release it.
  await hide(first);
  await blur(first);
  await focus(second);
  await type(second, 'Frage');
  await expect(suggestions(second, 'Frage')).toHaveCount(1, { timeout: TURN_MS });

  // And back again: the first tab returns, the second steps aside.
  await hide(second);
  await blur(second);
  await show(first);
  await focus(first);
  await type(first, 'Fehler');
  await expect(suggestions(first, 'Fehler')).toHaveCount(1, { timeout: TURN_MS });
});

test('focus flickering back and forth leaves the foreground tab working', async ({ context }) => {
  const first = await openHome(context);
  await type(first, 'Fenster');
  await expect(suggestions(first, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
  const second = await openHome(context);
  await focusOnly(first, second);

  // Focus is lost far more often than visibility — a click in the address
  // bar, a glance at devtools — so this is the flicker that matters now.
  // Twelve changes with nothing waited on in between, so each take and give
  // overlaps the next. The lock serialises them; nothing is left half-held.
  for (let i = 0; i < 3; i++) {
    await blur(first);
    await focus(second);
    await blur(second);
    await focus(first);
  }

  await type(first, 'Haus');
  await expect(suggestions(first, 'Haus')).toHaveCount(1, { timeout: TURN_MS });
});

test('with no tab in the foreground, the first one back takes the dictionary', async ({ context }) => {
  const first = await openHome(context);
  await type(first, 'Fenster');
  await expect(suggestions(first, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
  const second = await openHome(context);

  // The whole browser window goes behind something else: both tabs still
  // visible, neither holding the keyboard. Nobody holds the pool and nobody
  // needs it — a resting state, not a fault.
  await blur(first);
  await blur(second);
  await nothingHappensFor(second, 500);

  await focus(second);
  await type(second, 'Hund');
  await expect(suggestions(second, 'Hund')).toHaveCount(1, { timeout: TURN_MS });
});

// What this pins is the outcome, not the mechanism: a tab that was never in
// front shows nothing, and shows something once it is. Which of the two guards
// produced that — the startup value of `foreground`, or the
// `if (!foreground) return` at the top of the lock callback — is not
// observable from out here, and this test deliberately does not claim to say.
//
// Measured, because the distinction is tempting to assert and would be wrong:
// a build that starts `foreground = true` and calls `acquire()` from `start()`
// passes this too. Its lock request is granted a task later than the page's
// first report is delivered, so the early return catches it. The contract
// worth locking is the behaviour; nothing else in this file covers a tab that
// boots out of the foreground at all.
test('a tab that boots in the background has no dictionary until it is looked at', async ({ context }) => {
  const page = await openHome(context, { hidden: true, focused: false });

  await type(page, 'Fenster');
  await nothingHappensFor(page, SILENCE_MS);
  await expect(options(page)).toHaveCount(0);

  await show(page);
  await focus(page);
  await expect(suggestions(page, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
});

test('closing the tab that holds the dictionary releases it', async ({ context }) => {
  const holder = await openHome(context);
  await type(holder, 'Fenster');
  await expect(suggestions(holder, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });

  const successor = await openHome(context);
  // Killed rather than backgrounded: no handler of ours runs, and the lock has
  // to come back from the browser.
  await holder.close();

  await type(successor, 'Fenster');
  await expect(suggestions(successor, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES, { timeout: TURN_MS });
});
