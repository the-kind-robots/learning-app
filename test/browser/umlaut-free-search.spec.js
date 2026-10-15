const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// Fixture: «für», «schön» and «schon» are lemmas of their own (#578).

const options = (page) => page.getByRole('option');
const valueField = (page) => page.getByLabel('Слово (немецкий)');

async function seedWord(page, value, translation) {
  await page.evaluate(
    async ([value, translation]) => {
      const now = new Date().toISOString();
      await db.bulk_docs(db.use('user-db'), [
        {
          _id: 'vocab:' + value,
          type: 'vocab',
          value,
          translation: [{ lang: 'ru', value: translation }],
          created_at: now,
          modified_at: now,
        },
      ]);
    },
    [value, translation],
  );
}

test.describe('Поиск без умлаутов', () => {
  test('пользователь печатает слово без умлаута → в подсказках слово с умлаутом первым', async ({ page }) => {
    await test.step('Дано главная со словарём, где есть «für»', async () => {
      await openHome(page);
      await expect(valueField(page)).toBeVisible();
    });

    await test.step('Когда он печатает «fur»', async () => {
      await valueField(page).pressSequentially('fur');
      await expect(options(page).first()).toBeVisible();
    });

    await test.step('Тогда первая подсказка — «für»', async () => {
      await expect(options(page).first()).toHaveText('für');
    });

    await test.step('Когда он стирает слово и печатает «fuer»', async () => {
      await valueField(page).fill('');
      await valueField(page).pressSequentially('fuer');
    });

    await test.step('Тогда первая подсказка по-прежнему «für»', async () => {
      await expect(options(page).first()).toHaveText('für');
    });
  });

  test('пользователь печатает «schon» → в подсказках и «schon», и «schön»', async ({ page }) => {
    await test.step('Дано главная со словарём, где есть «schon» и «schön»', async () => {
      await openHome(page);
      await expect(valueField(page)).toBeVisible();
    });

    await test.step('Когда он печатает «schon»', async () => {
      await valueField(page).pressSequentially('schon');
      await expect(options(page)).toHaveCount(2);
    });

    await test.step('Тогда среди подсказок есть оба слова', async () => {
      await expect(options(page).filter({ hasText: /^schon$/ })).toHaveCount(1);
      await expect(options(page).filter({ hasText: /^schön$/ })).toHaveCount(1);
    });
  });

  test('пользователь ищет своё слово без умлаута → фильтр его находит', async ({ page }) => {
    await test.step('Дано сохранённые слова «für» и «Hund»', async () => {
      await page.goto('/');
      await seedWord(page, 'für', 'для');
      await seedWord(page, 'Hund', 'пёс');
      await page.goto('/words');
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
      await expect(page.locator('li.word-item')).toHaveCount(2);
    });

    await test.step('Когда он вводит в поиск «fur»', async () => {
      await page.getByPlaceholder('Поиск').fill('fur');
    });

    await test.step('Тогда показано только «für»', async () => {
      await expect(page.locator('li.word-item')).toHaveCount(1);
      await expect(page.locator('li.word-item')).toContainText('für');
    });

    await test.step('Когда он вводит в поиск «fuer»', async () => {
      await page.getByPlaceholder('Поиск').fill('fuer');
    });

    await test.step('Тогда «für» по-прежнему найдено', async () => {
      await expect(page.locator('li.word-item')).toHaveCount(1);
      await expect(page.locator('li.word-item')).toContainText('für');
    });
  });
});
