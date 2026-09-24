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

  await page.getByRole('button', { name: 'Открыть наборы' }).click();

  for (const name of names) {
    await expect(page.getByRole('button', { name: name + ' 0', exact: true })).toBeInViewport({ ratio: 1 });
  }
  await expect(page.getByRole('button', { name: 'Новый набор' })).toBeInViewport({ ratio: 1 });

  // The tiles are poured into CSS columns, and a tile broken across a column
  // boundary renders as two fragments — two client rects for one element.
  const fragments = await page.locator('.tile').evaluateAll((tiles) => tiles.map((tile) => tile.getClientRects().length));
  expect(fragments.length).toBe(names.length + 1);
  expect(fragments.filter((n) => n !== 1)).toEqual([]);
});

// The issue's repro on a phone (#460): the icon is tapped while the caret is
// still in the heading, so the rename's write lands after the themes screen
// has read its collections.
test('a rename left in the heading shows on the themes screen tapped open from it', async ({ page }) => {
  await page.goto('/');
  await seedCollections(page, ['xxx, aaa']);
  await page.getByRole('button', { name: 'Открыть наборы' }).tap();
  await page.getByRole('button', { name: 'xxx, aaa 0', exact: true }).tap();

  const heading = page.getByRole('heading', { name: 'xxx, aaa' });
  await expect(heading).toBeVisible();
  await heading.tap();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('xxx / aaa');
  await page.getByRole('button', { name: 'Открыть наборы' }).tap();

  await expect(page.getByRole('heading', { name: 'xxx 0', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'aaa 0', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /xxx, aaa/ })).toHaveCount(0);
});
