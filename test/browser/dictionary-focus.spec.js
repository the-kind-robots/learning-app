const { test, expect, nothingHappensFor } = require('./fixtures');

// The dictionary belongs to the tab being typed into (GH-351).
//
// `opfs-sahpool` admits one holder, so a tab takes the pool lock when it comes
// to the foreground — on screen and holding the keyboard — and gives it back
// when it leaves. A tab waiting its turn has no dictionary, and a query asked
// of it then is answered with no completions and forgotten; getting an answer
// after the turn arrives takes another keystroke.
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

// One attempt: the 100 ms suggest debounce, the round trip and the render.
// Measured at 9-44 ms past the debounce (#195), so this is loose on purpose.
const ANSWER_MS = 1000;

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
const openHome = async (context) => {
  const page = await context.newPage();
  await page.addInitScript(PAGE_STATE_SHIM, { hidden: false, focused: true });
  await page.goto('/home');
  await expect(valueField(page)).toBeVisible();
  return page;
};

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

// A query is answered from what the tab has when it arrives, and nothing is
// kept: one sent while the turn is still being taken comes back empty and is
// never replayed. A user looking at an empty list types the word again, and so
// does this — until the answer comes or the turn never does.
const typeUntilAnswered = async (page, word, count) => {
  const deadline = Date.now() + TURN_MS;
  for (;;) {
    await type(page, word);
    try {
      await expect(suggestions(page, word)).toHaveCount(count, { timeout: ANSWER_MS });
      return;
    } catch (error) {
      if (Date.now() >= deadline) throw error;
    }
  }
};

test.describe('Подсказки слов в нескольких вкладках', () => {
  test('пользователь печатает в двух видимых вкладках → подсказывает та, где клавиатура, и переходит за ним', async ({ context }) => {
    let left, right;

    await test.step('Дано две видимые вкладки главной (разделённый экран), клавиатура в левой', async () => {
      // Neither tab is hidden at any point here, so `document.hidden` is false
      // for both and cannot decide between them.
      left = await openHome(context);
      right = await openHome(context);
      await focusOnly(left, right);
    });

    await test.step('Когда он печатает «Fenster» в левой вкладке', async () => {
      await typeUntilAnswered(left, 'Fenster', FIXTURE_FENSTER_MATCHES);
    });

    await test.step('Тогда левая вкладка показывает две подсказки', async () => {
      await expect(suggestions(left, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES);
    });

    await test.step('Когда он печатает «Frage» в правой вкладке, где клавиатуры нет', async () => {
      await type(right, 'Frage');
      await nothingHappensFor(right, SILENCE_MS);
    });

    await test.step('Тогда правая вкладка подсказок не показывает', async () => {
      await expect(options(right)).toHaveCount(0);
    });

    await test.step('Когда он переходит в правую вкладку и печатает «Frage» снова', async () => {
      // Nothing changed about what is on screen; the dictionary still has to
      // move, and the word asked again — the first answer was an empty one.
      await focusOnly(right, left);
      await typeUntilAnswered(right, 'Frage', 1);
    });

    await test.step('Тогда правая вкладка показывает подсказку', async () => {
      await expect(suggestions(right, 'Frage')).toHaveCount(1);
    });
  });

  test('пользователь закрывает вкладку, где работали подсказки → в оставшейся подсказки работают', async ({ context }) => {
    let holder, successor;

    await test.step('Дано две вкладки, в первой подсказки работают', async () => {
      holder = await openHome(context);
      await typeUntilAnswered(holder, 'Fenster', FIXTURE_FENSTER_MATCHES);
      successor = await openHome(context);
    });

    await test.step('Когда он закрывает первую вкладку', async () => {
      // Killed rather than backgrounded: no handler of ours runs, and the
      // lock has to come back from the browser.
      await holder.close();
    });

    await test.step('Тогда во второй вкладке «Fenster» даёт две подсказки', async () => {
      await typeUntilAnswered(successor, 'Fenster', FIXTURE_FENSTER_MATCHES);
      await expect(suggestions(successor, 'Fenster')).toHaveCount(FIXTURE_FENSTER_MATCHES);
    });
  });
});
