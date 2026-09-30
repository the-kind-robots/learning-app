const { test, expect } = require('./fixtures');

// Watches the document from before the app boots and notes the first moment
// the splash, a loading state and the first tile are in the DOM. The
// splash may live for milliseconds on a small vocabulary, so a polling
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
    if (!window.__seen.splash && document.querySelector('.app-loading')) {
      window.__seen.splash = performance.now();
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

// The themes screen has no loading state of its own: the app opens on a
// splash until the words and collections are read (#494), and the tiles are
// there when the screen is.
test('the themes screen opened at start shows the splash, then its tiles, never a loading state', async ({ page }) => {
  await page.addInitScript(watchSwitcher);
  await page.goto('/');
  await addWord(page, 'der Hund', 'пёс');
  await seedVocabulary(page, 200);

  await page.goto('/collections');

  // One tile: «Всё подряд» with the 201 words.
  await expect(page.getByRole('button', { name: 'Всё подряд 201', exact: true })).toBeVisible({ timeout: 30000 });

  const seen = await page.evaluate(() => window.__seen);
  expect(seen.splash, 'the splash entered the DOM').toBeDefined();
  expect(seen.tile, 'a tile entered the DOM').toBeDefined();
  expect(seen.splash).toBeLessThan(seen.tile);
  expect(seen.loading, 'no loading state of its own').toBeUndefined();
});
