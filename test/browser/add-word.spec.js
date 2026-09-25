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

// GH-313: a write that fails used to leave the form looking untouched. While
// the switch is on, every read-write IndexedDB transaction is refused, the
// way a full disk refuses it; reads still run, so the save gets as far as its
// write. The switch goes on once the form is up — boot writes too.
test('a failed save says so, keeps the input, and a later save clears it', async ({ page }) => {
  await page.addInitScript(() => {
    const transaction = IDBDatabase.prototype.transaction;
    window.__refuseWrites = false;
    IDBDatabase.prototype.transaction = function (stores, mode, ...rest) {
      if (window.__refuseWrites && mode === 'readwrite') {
        throw new DOMException('Writes are refused', 'QuotaExceededError');
      }
      return transaction.call(this, stores, mode, ...rest);
    };
  });
  await page.goto('/home');

  const word = page.getByLabel('Слово (немецкий)');
  const translation = page.getByLabel('Перевод (русский)');
  const error = page.getByRole('alert');

  await expect(word).toBeVisible();
  await page.evaluate(() => { window.__refuseWrites = true; });
  await word.fill('Baum');
  await translation.fill('дерево');
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();

  await expect(error).toHaveText('Слово не сохранилось: в приложении сбой, и это не ваша ошибка.');
  await expect(word).toHaveValue('Baum');
  await expect(translation).toHaveValue('дерево');

  await page.evaluate(() => { window.__refuseWrites = false; });
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();

  await expect(error).toHaveCount(0);
  await expect(word).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();
});
