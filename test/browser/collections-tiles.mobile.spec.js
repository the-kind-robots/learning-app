const { test, expect } = require('@playwright/test');

// The issue's phone: 384 × 800. The mobile project's 390 × 844 is close,
// but the acceptance names this size.
test.use({ viewport: { width: 384, height: 800 } });

async function seedCollections(page, names) {
  await page.evaluate(async (names) => {
    const now = new Date().toISOString();
    const docs = names.map((name, i) => ({
      _id: 'collection:' + i, type: 'collection', name, word_ids: [], created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, names);
}

const names = ['Alltag', 'Arbeit', 'Bahn', 'Essen', 'Familie', 'Garten', 'Haus', 'Kino', 'Reise', 'Schule', 'Sport', 'Wetter'];

test('twelve collections fit a phone screen without scrolling', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, names);

  await page.getByRole('link', { name: 'Открыть наборы' }).click();

  for (const name of names) {
    await expect(page.getByRole('button', { name: name + ' 0', exact: true })).toBeInViewport({ ratio: 1 });
  }
  await expect(page.getByRole('button', { name: 'Новый набор' })).toBeInViewport({ ratio: 1 });
});
