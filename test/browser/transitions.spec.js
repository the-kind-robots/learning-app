const { test, expect } = require('@playwright/test');

// Every screen transition puts the target screen, with its data, in the page
// before the first animation frame after the tap (#494). The tap and the
// frame callback are scheduled in the same task, so the verdict does not
// depend on how fast the machine is: the data is either in the DOM by then or
// it is not. Measured on the vocabulary of the issue's measurement.

const WORDS = 1500;
const REVIEWS_PER_WORD = [5, 4]; // 1100 words × 5 + 400 × 4 = 7100 reviews

// Seeded at the engine level (test/browser/README.md, "Seeding from a spec"),
// in raw PouchDB form: the keys are the stored, snake-cased ones.
async function seedVocabulary(page) {
  await page.evaluate(async ({ words, perWord }) => {
    const user = db.use('user-db');
    const now = Date.now();
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    const vocab = [];
    const reviews = [];
    for (let i = 0; i < words; i++) {
      const value = 'wort' + letters[i % 26] + letters[Math.floor(i / 26) % 26] + i;
      const id = 'vocab:' + value;
      const created = new Date(now - (i + 10) * 86400000).toISOString();
      vocab.push({ _id: id, type: 'vocab', kind: 'word', value, translation: [{ lang: 'ru', value: 'слово' + i }], created_at: created, modified_at: created });
      const n = i < 1100 ? perWord[0] : perWord[1];
      for (let r = 0; r < n; r++) {
        reviews.push({ type: 'review', word_id: id, retained: r % 3 !== 0, translation: 'слово' + i, created_at: new Date(now - (n - r) * 3 * 86400000 - i * 1000).toISOString() });
      }
    }
    const themes = [];
    for (let c = 0; c < 6; c++) {
      themes.push({ type: 'collection', name: 'Thema ' + c, created_at: new Date(now - c * 1000).toISOString(), word_ids: vocab.slice(c * 100, c * 100 + 100).map((w) => w._id) });
    }
    for (let i = 0; i < vocab.length; i += 500) await user.bulkDocs(vocab.slice(i, i + 500));
    for (let i = 0; i < reviews.length; i += 1000) await user.bulkDocs(reviews.slice(i, i + 1000));
    await user.bulkDocs(themes);
  }, { words: WORDS, perWord: REVIEWS_PER_WORD });
}

// Memory is loaded when the metrics say so (a development build).
const memoryReady = (page) => page.waitForFunction(
  () => typeof window.__metrics === 'function' && window.__metrics().memory['ready-ms'],
  null,
  { timeout: 60000 },
);

// Clicks `clickSel` and reports whether `dataSel` was in the DOM when the
// first animation frame after the click ran.
function dataAtFirstFrame(page, clickSel, dataSel) {
  return page.evaluate(({ clickSel, dataSel }) => new Promise((resolve) => {
    const target = document.querySelector(clickSel);
    if (!target) {
      resolve(`nothing to click at ${clickSel}`);
      return;
    }
    requestAnimationFrame(() => resolve(!!document.querySelector(dataSel)));
    target.click();
  }), { clickSel, dataSel });
}

const CLOSE = 'button[aria-label="Закрыть"]';
const HOME_DATA = '#home-words-button:not(.home__words-button--hidden)';
const WORD_ROW = '.word-item';
const FIRST_TRIAL = '.lesson__prompt';
const THEME_TILE = '.masonry [data-collection-id]:not([data-collection-id="main"])';

test('every transition shows the target screen with its data at the first frame', async ({ page }) => {
  test.setTimeout(120000);
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
  await seedVocabulary(page);
  await page.goto('/home');
  await memoryReady(page);
  await expect(page.locator(HOME_DATA)).toBeVisible();

  const transitions = [
    ['home → words', '#home-words-button', WORD_ROW],
    ['words → home', CLOSE, HOME_DATA],
    ['home → lesson', '.home__lesson-button', FIRST_TRIAL],
    ['lesson → home', CLOSE, HOME_DATA],
    ['home → themes', 'button[aria-label="Открыть наборы"]', THEME_TILE],
    ['themes → home', CLOSE, HOME_DATA],
    ['home → words', '#home-words-button', WORD_ROW],
    ['words → lesson', '.vocabulary__start', FIRST_TRIAL],
    ['lesson → home', CLOSE, HOME_DATA],
  ];
  for (const [name, click, data] of transitions) {
    expect(await dataAtFirstFrame(page, click, data), name).toBe(true);
    // The step back a close takes lands with `popstate`; the next tap starts
    // from the settled page.
    await expect(page.locator(data).first()).toBeAttached();
    await page.waitForLoadState('domcontentloaded');
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 50)));
  }
  await expect(page).toHaveURL(/\/home$/);
});

test('a keystroke in the words filter shows its rows at the first frame', async ({ page }) => {
  test.setTimeout(120000);
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
  await seedVocabulary(page);
  await page.goto('/words');
  await memoryReady(page);
  await expect(page.locator(WORD_ROW).first()).toBeVisible();

  const matched = await page.evaluate(() => new Promise((resolve) => {
    const input = document.querySelector('input[placeholder="Поиск"]');
    requestAnimationFrame(() => {
      const values = [...document.querySelectorAll('.word-item__value')].map((n) => n.textContent);
      resolve(values.length > 0 && values.every((v) => v.includes('wortq')));
    });
    input.value = 'wortq';
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }));
  expect(matched).toBe(true);
});
