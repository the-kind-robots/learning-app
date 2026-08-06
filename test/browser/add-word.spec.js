const { test, expect } = require('@playwright/test');

test('adding a word persists it to the words list', async ({ page }) => {
  await page.goto('/home');

  await page.getByLabel('Слово (немецкий)').fill('Haus');
  await page.getByLabel('Перевод (русский)').fill('дом');
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();

  // The button is hidden while the vocabulary is empty, so its appearance
  // already proves the write landed.
  await page.getByRole('button', { name: 'Список слов' }).click();
  await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible();
  const item = page.getByRole('listitem').filter({ hasText: 'Haus' });
  await expect(item).toBeVisible();
  await expect(item).toContainText('дом');

  // Persistence, not just render state: the word survives a full reload.
  await page.reload();
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Haus' })
  ).toBeVisible();
});
