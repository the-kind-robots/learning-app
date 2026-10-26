const { test, expect } = require('./fixtures');
const { EXAMPLES, seedWord } = require('./word-example.shared');

// GH-444: a lesson on a folder's parent asks the example of a child, as the word dialog shows it.

test.describe('Пример в уроке папки', () => {
  test('ученик начинает урок на папке со словом только в дочерней теме → после слова получает пример дочерней темы', async ({ page }) => {
    await test.step('Дано папка «Reisen» активна, слово лежит только в «Reisen/Bahn» с её примером', async () => {
      await seedWord(page, {
        active: 'collection:reisen',
        collections: [{ id: 'collection:reisen', name: 'Reisen', words: [] }, { id: 'collection:bahn', name: 'Reisen/Bahn' }],
        examples: [EXAMPLES[0]],
      });
    });

    await test.step('Когда он верно переводит слово и идёт дальше', async () => {
      await page.goto('/lesson');
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('Bahnhof');
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
      await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
    });

    await test.step('Тогда он переводит предложение дочерней темы', async () => {
      await expect(page.locator('.lesson__instruction')).toContainText('предложение');
      await expect(page.locator('.lesson__prompt')).toHaveText(EXAMPLES[0].translation);
    });
  });
});
