const { test, expect, memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

const rows = (page) => page.locator('.word-item');

// Seeded at the engine level (README, "Seeding from a spec"): two themes
// that both hold the misspelt word, the first one active.
async function seedThemes(page) {
  await page.evaluate(async () => {
    const now = new Date().toISOString();
    await db.bulk_docs(db.use('user-db'), [
      { _id: 'collection:bahn', type: 'collection', name: 'Bahn', word_ids: ['vocab:bahnhoff'], created_at: now },
      { _id: 'collection:alltag', type: 'collection', name: 'Alltag', word_ids: ['vocab:bahnhoff'], created_at: now },
    ]);
  });
}

async function openWord(page, text) {
  await page.locator('.word-item__display', { hasText: text }).first().click();
}

test.describe('Исправление написания слова', () => {
  test('пользователь исправляет слово → новое написание в списке и в тех же темах, старое не возвращается после перезагрузки', async ({ page }) => {
    await test.step('Дано слово «Bahnhoff» лежит в темах «Bahn» и «Alltag», тема «Bahn» активна', async () => {
      await page.addInitScript(() => {
        if (!sessionStorage.getItem('seeded')) {
          localStorage.setItem('active-collection-id', 'collection:bahn');
          sessionStorage.setItem('seeded', '1');
        }
      });
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Bahnhoff', 'вокзал');
      await seedThemes(page);
      await page.goto('/words');
      await expect(rows(page)).toHaveCount(1, { timeout: 20000 });
    });

    await test.step('Когда он меняет «Bahnhoff» на «Bahnhof» и сохраняет', async () => {
      await openWord(page, 'Bahnhoff');
      await page.getByLabel('Слово', { exact: true }).fill('Bahnhof');
      await expect(page.getByText('Сохранится как новое слово в тех же темах. «Bahnhoff» удалится вместе с прогрессом и примерами.')).toBeVisible();
      await page.getByRole('button', { name: 'Сохранить' }).click();
    });

    await test.step('Тогда в списке «Bahnhof», а «Bahnhoff» нет', async () => {
      await expect(rows(page)).toHaveCount(1);
      await expect(page.getByText('Bahnhof', { exact: true })).toBeVisible();
      await expect(page.getByText('Bahnhoff', { exact: true })).toHaveCount(0);
    });

    await test.step('Тогда после перезагрузки «Bahnhof» есть в теме «Bahn» и в теме «Alltag», а «Bahnhoff» нет', async () => {
      await page.goto('/words');
      await expect(page.getByText('Bahnhof', { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText('Bahnhoff', { exact: true })).toHaveCount(0);
      await page.evaluate(() => localStorage.setItem('active-collection-id', 'collection:alltag'));
      await page.reload();
      await expect(page.getByText('Bahnhof', { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText('Bahnhoff', { exact: true })).toHaveCount(0);
    });
  });

  test('пользователь вводит уже существующее слово → видит «Такое слово уже есть», сохранить нельзя', async ({ page }) => {
    await test.step('Дано слова «Bahnhoff» и «Bahnhof», открыт список слов', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Bahnhoff', 'вокзал');
      await addWord(page, 'Bahnhof', 'станция');
      await page.goto('/words');
      await expect(rows(page)).toHaveCount(2, { timeout: 20000 });
    });

    await test.step('Когда он меняет «Bahnhoff» на «Bahnhof»', async () => {
      await openWord(page, 'Bahnhoff');
      await page.getByLabel('Слово', { exact: true }).fill('Bahnhof');
    });

    await test.step('Тогда видит «Такое слово уже есть», а «Сохранить» неактивна', async () => {
      await expect(page.getByText('Такое слово уже есть')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Сохранить' })).toBeDisabled();
    });
  });
});
