const { test, expect, memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

const rows = (page) => page.locator('.word-item');

// Seeded at the engine level (README, "Seeding from a spec"): a folder and
// its child, the child holding the word.
async function seedFolder(page) {
  await page.evaluate(async () => {
    const now = new Date().toISOString();
    await db.bulk_docs(db.use('user-db'), [
      { _id: 'collection:parent', type: 'collection', name: 'Грамматика', word_ids: [], created_at: now },
      { _id: 'collection:child', type: 'collection', name: 'Грамматика/Союзы', word_ids: ['vocab:bischer'], created_at: now },
    ]);
  });
}

test.describe('Удаление слова из набора с вложенными наборами', () => {
  test('пользователь убирает слово из набора, где оно лежит во вложенном → слово исчезает из списка и после перезагрузки, но остаётся в «Всё подряд»', async ({ page }) => {
    await test.step('Дано набор «Грамматика» активен, слово «bischer» лежит в «Грамматика/Союзы»', async () => {
      await page.addInitScript(() => {
        if (!sessionStorage.getItem('seeded')) {
          localStorage.setItem('active-collection-id', 'collection:parent');
          sessionStorage.setItem('seeded', '1');
        }
      });
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'bischer', 'до сих пор');
      await seedFolder(page);
      await page.goto('/words');
      await expect(rows(page)).toHaveCount(1, { timeout: 20000 });
    });

    await test.step('Когда он убирает слово из набора', async () => {
      let prompt = null;
      page.once('dialog', (dialog) => { prompt = dialog.message(); dialog.accept(); });
      await page.locator('.word-item__display').first().click();
      await page.locator('.word-edit-dialog__delete').click();
      await expect.poll(() => prompt).toBe('Убрать «bischer» из набора?');
    });

    await test.step('Тогда в списке слов нет', async () => {
      await expect(rows(page)).toHaveCount(0);
    });

    await test.step('Тогда после перезагрузки слова в списке тоже нет', async () => {
      await page.goto('/words');
      await expect(page.getByText('Слов пока нет')).toBeVisible({ timeout: 20000 });
      await expect(rows(page)).toHaveCount(0);
    });

    await test.step('Тогда в «Всё подряд» слово есть', async () => {
      await page.goto('/collections');
      await page.getByRole('button', { name: /^Всё подряд/ }).click();
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(rows(page)).toHaveCount(1);
      await expect(page.getByText('bischer')).toBeVisible();
    });
  });
});
