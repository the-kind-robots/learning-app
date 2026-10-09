const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// Watches the document from before the app boots and notes the first loading
// state and the first tile that enter the DOM. Either may live for
// milliseconds, so a polling assertion could miss it; a MutationObserver
// cannot. Attributes are watched too: the renderer morphs nodes in place, so
// a block can appear as a class change on an existing element without any
// node being inserted. The observer is attached to `document`: an init script
// runs before the document has an element, so `document.documentElement` is
// still null here.
const watchSwitcher = `
  window.__seen = {};
  new MutationObserver(() => {
    if (!window.__seen.loading && document.querySelector('.switcher__loading')) {
      window.__seen.loading = performance.now();
    }
    if (!window.__seen.tile && document.querySelector('.tile')) {
      window.__seen.tile = document.querySelector('.tile').textContent;
    }
  }).observe(document, {
    attributes: true, characterData: true, childList: true, subtree: true,
  });
`;

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

// The themes screen has no loading state of its own: the app opens on the
// server's splash until memory is loaded (#494, #515), and the first tile
// shown already counts every word. A tile shown from empty memory would count
// none.
test.describe('Загрузка тем', () => {
  test('пользователь открывает приложение сразу на экране тем → первая же плитка со всеми словами, без «загрузки»', async ({ page }) => {
    await test.step('Дано 201 слово в словаре', async () => {
      await page.addInitScript(watchSwitcher);
      await page.goto('/');
      await addWord(page, 'der Hund', 'пёс');
      await seedVocabulary(page, 200);
    });

    await test.step('Когда он открывает /collections', async () => {
      await page.goto('/collections');
    });

    await test.step('Тогда видна плитка «Всё подряд 201»', async () => {
      await expect(page.getByRole('button', { name: 'Всё подряд 201', exact: true })).toBeVisible({ timeout: 30000 });
    });

    await test.step('Тогда первая показанная плитка уже считала все слова, а состояния загрузки не было', async () => {
      const seen = await page.evaluate(() => window.__seen);
      expect(seen.tile, 'the first tile counts every word').toContain('201');
      expect(seen.loading, 'no loading state of its own').toBeUndefined();
    });
  });
});
