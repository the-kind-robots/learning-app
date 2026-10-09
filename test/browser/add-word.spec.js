const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

test.describe('Добавление слова', () => {
  test('пользователь добавляет слово → оно в списке слов и остаётся после перезагрузки', async ({ page }) => {
    await test.step('Дано главная', async () => {
      await page.goto('/home');
    });

    await test.step('Когда он добавляет слово «Haus — дом»', async () => {
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Тогда в списке слов есть «Haus» с переводом «дом»', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible();
      const item = page.getByRole('listitem').filter({ hasText: 'Haus' });
      await expect(item).toBeVisible();
      await expect(item).toContainText('дом');
    });

    await test.step('Когда он перезагружает страницу', async () => {
      await page.reload();
    });

    await test.step('Тогда слово всё ещё в списке', async () => {
      await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
    });
  });

  // GH-313: a write that fails used to leave the form looking untouched. While
  // the switch is on, every read-write IndexedDB transaction is refused, the
  // way a full disk refuses it; reads still run, so the save gets as far as its
  // write. The switch goes on once the form is up — boot writes too.
  test('пользователь добавляет слово при сбое записи → видит ошибку, ввод сохранён, повтор проходит', async ({ page }) => {
    const word = page.getByLabel('Слово (немецкий)');
    const translation = page.getByLabel('Перевод (русский)');
    const error = page.getByRole('alert');

    await test.step('Дано главная, где запись на диск отклоняется', async () => {
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
      await expect(word).toBeVisible();
      await page.evaluate(() => { window.__refuseWrites = true; });
    });

    await test.step('Когда он пытается добавить «Baum — дерево»', async () => {
      await word.fill('Baum');
      await translation.fill('дерево');
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда он видит ошибку, а введённое остаётся в полях', async () => {
      await expect(error).toHaveText('Слово не сохранилось: в приложении сбой, и это не ваша ошибка.');
      await expect(word).toHaveValue('Baum');
      await expect(translation).toHaveValue('дерево');
    });

    await test.step('Когда запись снова работает и он повторяет', async () => {
      await page.evaluate(() => { window.__refuseWrites = false; });
      await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
    });

    await test.step('Тогда ошибки нет и поля очищены', async () => {
      await expect(error).toHaveCount(0);
      await expect(word).toHaveValue('');
      await expect(page.getByRole('button', { name: 'Список слов' })).toBeVisible();
    });
  });
});
