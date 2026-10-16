const { test, expect } = require('./fixtures');
const { fillTranslation } = require('./add-form.shared');
const { becomeAccount } = require('./account.shared');
const { openHome } = require('./service-worker.shared');

// GH-585: `rauch` the adjective and `der Rauch` share a spelling, and the
// backend guessed between them. A suggestion that is picked says which one the
// learner means, and the example request carries it. The fixture has the same
// pair in `schon` (adverb) and `schön` (adjective).

const valueField = (page) => page.getByLabel('Слово (немецкий)');

async function stubExampleEndpoint(page) {
  const requested = [];
  await page.route('**/api/examples*', async (route) => {
    requested.push(route.request().url());
    await route.fulfill({ status: 422, contentType: 'application/json', body: '{"error":"stub"}' });
  });
  return requested;
}

test.describe('Часть речи в запросе примера', () => {
  test('пользователь выбрал подсказку «schön» и добавил слово → запрос примера несёт pos=adjective', async ({ page }) => {
    let requested;

    await test.step('Дано главная с аккаунтом и набранное «schon»', async () => {
      requested = await stubExampleEndpoint(page);
      await openHome(page);
      await becomeAccount(page);
      await valueField(page).pressSequentially('schon');
    });

    await test.step('Когда он выбирает подсказку «schön» и добавляет слово', async () => {
      const picked = page.getByRole('option').filter({ hasText: 'schön' }).first();
      await expect(picked).toBeVisible();
      await picked.click();
      await expect(valueField(page)).toHaveValue('schön');
      await fillTranslation(page, 'красивый');
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда запрос примера несёт часть речи выбранной подсказки', async () => {
      await expect.poll(() => requested.length, { timeout: 15000 }).toBeGreaterThan(0);
      const asked = new URL(requested[0], 'http://localhost');
      expect(asked.searchParams.get('word')).toBe('schön');
      expect(asked.searchParams.get('pos')).toBe('adjective');
    });
  });

  test('пользователь набрал слово сам, не выбирая подсказку → запрос примера без pos', async ({ page }) => {
    let requested;

    await test.step('Дано главная с аккаунтом', async () => {
      requested = await stubExampleEndpoint(page);
      await openHome(page);
      await becomeAccount(page);
    });

    await test.step('Когда он набирает «schön» и добавляет слово', async () => {
      await valueField(page).pressSequentially('schön');
      await fillTranslation(page, 'красивый');
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда запрос примера не называет часть речи', async () => {
      await expect.poll(() => requested.length, { timeout: 15000 }).toBeGreaterThan(0);
      expect(new URL(requested[0], 'http://localhost').searchParams.has('pos')).toBe(false);
    });
  });
});
