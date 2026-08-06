const { test, expect } = require('@playwright/test');

test('route entry renders shell and lists', async ({ page }) => {
  // An unknown path is a redirect, not a visit.
  await page.goto('/');
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Sprecha' })).toBeVisible();

  // Direct entry to the words route loads its data (empty state here).
  await page.goto('/words');
  await expect(page.getByText('Слов пока нет')).toBeVisible();

  // And back: the empty-state action returns to home.
  await page.getByRole('button', { name: 'Добавить слово' }).click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
  await expect(page.getByLabel('Слово (немецкий)')).toBeVisible();
});
