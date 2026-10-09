const { test, expect } = require('./fixtures');

// A query that is not valid percent-encoding made the router throw while it
// read the address, and the app never started. The query is dropped and the
// screen of the path opens as usual (#515).

test.describe('Адрес с неверным запросом', () => {
  test('пользователь открывает адрес с битым запросом → видит главную без запроса, приложение работает', async ({ page }) => {
    await test.step('Когда он открывает /home?x=%', async () => {
      await page.goto('/home?x=%');
    });

    await test.step('Тогда видна главная с формой, а запрос убран из адреса', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      await expect(page).toHaveURL(/\/home$/);
      await expect(page.getByLabel('Слово (немецкий)')).toBeVisible();
    });

    await test.step('Когда он открывает наборы', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
    });

    await test.step('Тогда открыт экран наборов', async () => {
      await expect(page).toHaveURL(/\/collections$/);
    });
  });
});
