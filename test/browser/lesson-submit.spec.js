const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

test.describe('Отправка ответа с клавиатуры', () => {
  test('ученик вводит ответ и жмёт Ctrl+Enter → ответ проверен', async ({ page }) => {
    await test.step('Дано урок из одного слова и введённый верный ответ', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/lesson');
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
    });

    await test.step('Когда он жмёт Ctrl+Enter', async () => {
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).press('Control+Enter');
    });

    await test.step('Тогда он видит «Правильно!»', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });
  });

  test('ученик вводит ответ и жмёт Enter → ответ проверен', async ({ page }) => {
    await test.step('Дано урок из одного слова и введённый верный ответ', async () => {
      await page.goto('/home');
      await addWord(page, 'der Hund', 'пёс');
      await page.goto('/lesson');
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('der Hund');
    });

    await test.step('Когда он жмёт Enter', async () => {
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).press('Enter');
    });

    await test.step('Тогда он видит «Правильно!»', async () => {
      await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    });
  });
});
