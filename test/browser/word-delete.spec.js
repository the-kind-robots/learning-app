const { test, expect, memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

const rows = (page) => page.locator('.word-item');

// A second live revision of the word, as a replication that brought a
// sibling edit leaves it: PouchDB serves the winner and keeps the other.
async function addConflictingRevision(page, id) {
  await page.evaluate(async (id) => {
    const user = db.use('user-db');
    const doc = await user.get(id);
    const [gen] = doc._rev.split('-');
    await user.bulkDocs({
      docs: [{ ...doc, _rev: (Number(gen)) + '-' + 'f'.repeat(32), translation: [{ lang: 'ru', value: 'другая правка' }] }],
      new_edits: false,
    });
  }, id);
}

test.describe('Удаление слова из списка', () => {
  test('пользователь удаляет слово из отфильтрованного списка → слово исчезает и не возвращается после перезагрузки', async ({ page }) => {
    const errors = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', (e) => errors.push(String(e)));

    await test.step('Дано слова «bischer» и «Haus», открыт список слов', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'bischer', 'до сих пор');
      await addWord(page, 'Haus', 'дом');
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(rows(page)).toHaveCount(2);
    });

    await test.step('Когда он вводит «bischer» в фильтр и удаляет найденное слово', async () => {
      await page.getByPlaceholder('Поиск').fill('bischer');
      await expect(rows(page)).toHaveCount(1);
      page.once('dialog', (dialog) => dialog.accept());
      await page.locator('.word-item__display').first().click();
      await page.locator('.word-edit-dialog__delete').click();
    });

    await test.step('Тогда в списке нет слов, а «Ничего не найдено»', async () => {
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
      await expect(rows(page)).toHaveCount(0);
    });

    await test.step('Тогда после перезагрузки слова «bischer» нет', async () => {
      await page.goto('/words');
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
      await page.getByPlaceholder('Поиск').fill('bischer');
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
      expect(errors).toEqual([]);
    });
  });

  test('пользователь удаляет слово с конфликтующей ревизией → слово исчезает и не возвращается после перезагрузки', async ({ page }) => {
    await test.step('Дано слова «bischer» с двумя живыми ревизиями и «Haus», открыт список слов', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'bischer', 'до сих пор');
      await addWord(page, 'Haus', 'дом');
      await addConflictingRevision(page, 'vocab:bischer');
      await page.goto('/words');
      await expect(rows(page)).toHaveCount(2, { timeout: 20000 });
    });

    await test.step('Когда он удаляет слово', async () => {
      page.once('dialog', (dialog) => dialog.accept());
      await page.getByPlaceholder('Поиск').fill('bischer');
      await expect(rows(page)).toHaveCount(1);
      await page.locator('.word-item__display').first().click();
      await page.locator('.word-edit-dialog__delete').click();
    });

    await test.step('Тогда слово исчезло из списка', async () => {
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
    });

    await test.step('Тогда после перезагрузки слова нет', async () => {
      await page.goto('/words');
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible({ timeout: 20000 });
      await page.getByPlaceholder('Поиск').fill('bischer');
      await expect(page.getByText('Ничего не найдено')).toBeVisible();
    });
  });
});
