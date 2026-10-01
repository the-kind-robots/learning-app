const { test, expect } = require('./fixtures');

// A query that is not valid percent-encoding made the router throw while it
// read the address, and the app never started. The query is dropped and the
// screen of the path opens as usual (#515).

test('an address with a malformed query opens its screen without the query', async ({ page }) => {
  await page.goto('/home?x=%');
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByLabel('Слово (немецкий)')).toBeVisible();

  // The router runs: the themes control opens the themes screen.
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await expect(page).toHaveURL(/\/collections$/);
});
