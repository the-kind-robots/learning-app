const { test, expect } = require('@playwright/test');

// Watches the document from before the app boots and notes the first moment
// the themes screen's loading state and its first card are in the DOM. The
// loading state may live for milliseconds on a small vocabulary, so a polling
// assertion could miss it; a MutationObserver cannot. Attributes are watched
// too: the renderer morphs nodes in place, so a block can appear as a class
// change on an existing element without any node being inserted. The observer
// is attached to `document`: an init script runs before the document has an
// element, so `document.documentElement` is still null here.
const watchSwitcher = `
  window.__seen = {};
  new MutationObserver(() => {
    if (!window.__seen.loading && document.querySelector('.switcher__loading')) {
      window.__seen.loading = performance.now();
    }
    if (!window.__seen.card && document.querySelector('.tab-card')) {
      window.__seen.card = performance.now();
    }
  }).observe(document, {
    attributes: true, characterData: true, childList: true, subtree: true,
  });
`;

async function addWord(page, value, translation) {
  await page.getByLabel('Слово (немецкий)').fill(value);
  await page.getByLabel('Перевод (русский)').fill(translation);
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
}

// Enough words and reviews that reading them takes longer than a frame: on a
// handful the summary resolves before the next render and the loading state
// is never painted, which is the behaviour the requirement is about too —
// the loading state is for the seconds a real vocabulary takes. Seeded at
// the engine level (see README, "Seeding from a spec").
async function seedVocabulary(page, words) {
  await page.evaluate(async (n) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < n; i++) {
      docs.push({ _id: 'vocab:wort' + i, type: 'vocab', value: 'wort' + i, translation: [{ lang: 'ru', value: 'слово' + i }], created_at: now, modified_at: now });
      for (let k = 0; k < 6; k++) {
        docs.push({ type: 'review', word_id: 'vocab:wort' + i, retained: k % 2 === 0, created_at: now });
      }
    }
    await db.bulk_docs(db.use('user-db'), docs);
  }, words);
}

test('opening the themes screen shows a loading state before its cards', async ({ page }) => {
  await page.addInitScript(watchSwitcher);
  await page.goto('/');
  await addWord(page, 'der Hund', 'пёс');
  await seedVocabulary(page, 200);

  await page.getByRole('link', { name: 'Открыть наборы' }).click();

  // The first read after a seed also builds the views over every document,
  // which is seconds; the loading state is on screen for all of it.
  await expect(page.locator('.tab-card')).toHaveCount(2, { timeout: 30000 });
  await expect(page.locator('.switcher__loading')).toHaveCount(0);

  const seen = await page.evaluate(() => window.__seen);
  expect(seen.loading, 'the loading state entered the DOM').toBeDefined();
  expect(seen.card, 'a card entered the DOM').toBeDefined();
  expect(seen.loading).toBeLessThan(seen.card);
});
