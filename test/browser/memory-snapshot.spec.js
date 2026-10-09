const { test, expect } = require('./fixtures');

// A repeat start takes memory from a snapshot in the Cache API and catches up
// from its feed positions (#508, ADR-0018). A snapshot that fails a check is
// deleted, and every document is read.

const rows = (page) => page.locator('.word-item');

// Memory is loaded when the development build's metrics say so; they also
// say where memory came from.
async function memoryFrom(page) {
  await page.waitForFunction(
    () => typeof window.__metrics === 'function' && window.__metrics().memory['ready-ms'],
    null,
    { timeout: 60000 },
  );
  return page.evaluate(() => window.__metrics().memory.from);
}

// The stored snapshot as text, or '' when there is none.
const storedSnapshot = (page) => page.evaluate(async () => {
  const cache = await caches.open('learner-memory');
  const response = await cache.match('/learner-memory/snapshot');
  return response ? response.text() : '';
});

async function seedWords(page, n) {
  await page.evaluate(async (n) => {
    const now = new Date().toISOString();
    const docs = [];
    for (let i = 0; i < n; i++) {
      docs.push({ _id: 'vocab:wort' + (1000 + i), type: 'vocab', value: 'Wort' + (1000 + i), translation: [{ lang: 'ru', value: 'слово' }], created_at: now, modified_at: now });
    }
    await db.use('user-db').bulkDocs(docs);
  }, n);
}

// The first start reads every document and stores a snapshot. Then words are
// seeded and the page reloaded: that start takes the snapshot, catches up
// with the words, and stores a snapshot that holds them.
async function withSnapshot(page) {
  await page.goto('/home');
  expect(await memoryFrom(page)).toBe('databases');
  await expect.poll(() => storedSnapshot(page)).not.toBe('');
  await seedWords(page, 30);
  await page.reload();
  await memoryFrom(page);
  await expect.poll(() => storedSnapshot(page)).toContain('vocab:wort1029');
}

async function search(page, text) {
  await page.getByPlaceholder('Поиск').fill(text);
}

test.describe('Повторный запуск приложения', () => {
  test('пользователь снова открывает приложение → слова берутся из снимка и дополняются новыми', async ({ page }) => {
    await test.step('Дано приложение уже запускалось и сохранило снимок со словами', async () => {
      await withSnapshot(page);
    });

    await test.step('Когда слово появляется в базе мимо приложения и он открывает список слов', async () => {
      // Stored after the snapshot, past the app, as another tab or a pull does.
      await page.evaluate(async () => {
        const now = new Date().toISOString();
        await db.use('user-db').put({ _id: 'vocab:neu', type: 'vocab', value: 'neu', translation: [{ lang: 'ru', value: 'новый' }], created_at: now, modified_at: now });
      });
      await page.goto('/words');
      expect(await memoryFrom(page)).toBe('snapshot');
    });

    await test.step('Тогда в поиске находится и новое слово «neu», и старое «Wort1029»', async () => {
      await search(page, 'neu');
      await expect(rows(page)).toHaveCount(1);
      await search(page, 'Wort1029');
      await expect(rows(page)).toHaveCount(1);
    });
  });
});
