const { test, expect } = require('./fixtures');

async function seedWords(page, count) {
  await page.evaluate(async (count) => {
    const now = new Date().toISOString();
    const docs = Array.from({ length: count }, (_, i) => {
      const value = 'wort' + i;
      return { _id: 'vocab:' + value, type: 'vocab', value, translation: [{ lang: 'ru', value: 'слово' + i }], created_at: now, modified_at: now };
    });
    await db.bulk_docs(db.use('user-db'), docs);
  }, count);
}

test.describe('Шапка на телефоне', () => {
  test('пользователь переходит с экрана на экран → шапка везде одной высоты', async ({ page }) => {
    // The bar's box, its bottom line included.
    const barHeight = async () => (await page.locator('.app-bar').boundingBox()).height;
    const heights = {};

    await test.step('Дано три слова', async () => {
      await page.goto('/');
      await seedWords(page, 3);
    });

    await test.step('Когда он открывает главную, слова, темы и урок', async () => {
      await page.reload();
      await expect(page.getByRole('button', { name: 'Открыть наборы' })).toBeVisible();
      heights.home = await barHeight();

      await page.goto('/words');
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeAttached({ timeout: 20000 });
      heights.words = await barHeight();

      await page.goto('/');
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page.getByRole('button', { name: /^Всё подряд/ })).toBeVisible();
      heights.themes = await barHeight();

      await page.goto('/words');
      await page.getByRole('button', { name: /начать урок/i }).click();
      await expect(page.getByText('Переведите слово на немецкий')).toBeVisible({ timeout: 20000 });
      heights.lesson = await barHeight();
    });

    await test.step('Тогда шапка на каждом экране той же высоты, что на главной', async () => {
      expect(heights).toEqual({ home: heights.home, words: heights.home, themes: heights.home, lesson: heights.home });
    });
  });
});
