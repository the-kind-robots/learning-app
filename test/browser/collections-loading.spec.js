const { test, expect } = require('@playwright/test');

// Watches the document from before the app boots and notes the first moment
// the themes screen's loading state and its first tile are in the DOM. The
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
    if (!window.__seen.tile && document.querySelector('.tile')) {
      window.__seen.tile = performance.now();
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

// Enough words and reviews that loading them into memory takes longer than a
// frame. Seeded at the engine level (see README, "Seeding from a spec").
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

// The loading state is for the time the learner's data takes to reach memory
// at start (#494): a screen opened then opens at once and fills in. Opened
// once memory is ready, the tiles are there on the tap.
test('the themes screen opened before memory is ready shows a loading state, then its tiles', async ({ page }) => {
  await page.addInitScript(watchSwitcher);
  await page.goto('/');
  await addWord(page, 'der Hund', 'пёс');
  await seedVocabulary(page, 200);

  await page.goto('/collections');

  // One tile: «Всё подряд» with the 201 words.
  await expect(page.getByRole('button', { name: 'Всё подряд 201', exact: true })).toBeVisible({ timeout: 30000 });
  await expect(page.locator('.switcher__loading')).toHaveCount(0);

  const seen = await page.evaluate(() => window.__seen);
  expect(seen.loading, 'the loading state entered the DOM').toBeDefined();
  expect(seen.tile, 'a tile entered the DOM').toBeDefined();
  expect(seen.loading).toBeLessThan(seen.tile);
});
