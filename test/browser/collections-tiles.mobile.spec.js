const { test, expect } = require('./fixtures');

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

test.describe('Плитки тем на телефоне', () => {
  test('пользователь открывает экран из двенадцати тем → все плитки на экране, прокрутки нет', async ({ page }) => {
    await test.step('Дано двенадцать тем', async () => {
      await page.goto('/');
      await seedCollections(page, names);
    });

    await test.step('Когда он открывает экран тем', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда все плитки и кнопка «Новый набор» целиком в видимой области', async () => {
      for (const name of names) {
        await expect(page.getByRole('button', { name: name + ' 0', exact: true })).toBeInViewport({ ratio: 1 });
      }
      await expect(page.getByRole('button', { name: 'Новый набор' })).toBeInViewport({ ratio: 1 });
    });

    await test.step('Тогда ни одна плитка не разорвана между колонками', async () => {
      // The tiles are poured into CSS columns, and a tile broken across a
      // column boundary renders as two fragments — two client rects.
      const fragments = await page.locator('.tile').evaluateAll((tiles) => tiles.map((tile) => tile.getClientRects().length));
      expect(fragments.length).toBe(names.length + 1);
      expect(fragments.filter((n) => n !== 1)).toEqual([]);
    });
  });
});
