const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// A button with a hotkey names its key in its tooltip and in aria-keyshortcuts (#793).

const closeButton = (page) => page.getByRole('button', { name: 'Закрыть' });

// A lesson passed now, written straight into the user database, and home opened again so it reads it.
async function lessonToday(page) {
  await page.evaluate(async () => {
    await db.bulk_docs(db.use('user-db'), [
      { _id: 'lesson-seed-0', type: 'lesson', passed_at: new Date().toISOString() },
    ]);
  });
  await page.reload();
}

test.describe('Подсказки горячих клавиш', () => {
  test('пользователь наводит на кнопки главной → в подсказке каждой её клавиша', async ({ page }) => {
    await test.step('Дано слово и урок сегодня', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await lessonToday(page);
    });

    await test.step('Тогда у кнопок главной в подсказке их клавиши', async () => {
      const hints = [
        ['Статистика', 'Статистика · Alt+S', 'Alt+S'],
        ['Открыть наборы', 'Наборы · Alt+T', 'Alt+T'],
        ['Список слов', 'Список слов · Alt+W', 'Alt+W'],
        ['НАЧАТЬ УРОК', 'НАЧАТЬ УРОК · Alt+Enter', 'Alt+Enter'],
      ];
      for (const [name, title, keys] of hints) {
        const button = page.getByRole('button', { name, exact: true });
        await expect(button).toHaveAttribute('title', title);
        await expect(button).toHaveAttribute('aria-keyshortcuts', keys);
      }
    });
  });

  test('пользователь наводит на крестик списка слов → «Закрыть · Escape»', async ({ page }) => {
    await test.step('Дано открытый список слов', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page).toHaveURL(/\/words$/);
    });

    await test.step('Тогда у крестика в подсказке Escape', async () => {
      await expect(closeButton(page)).toHaveAttribute('title', 'Закрыть · Escape');
      await expect(closeButton(page)).toHaveAttribute('aria-keyshortcuts', 'Escape');
    });
  });

  test('ученик в уроке наводит на крестик → «Закрыть», без клавиши', async ({ page }) => {
    await test.step('Дано два слова и начатый урок', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await addWord(page, 'Hund', 'собака');
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(page).toHaveURL(/\/lesson$/);
    });

    await test.step('Тогда у крестика в подсказке только «Закрыть»', async () => {
      await expect(closeButton(page)).toHaveAttribute('title', 'Закрыть');
      await expect(closeButton(page)).not.toHaveAttribute('aria-keyshortcuts', /.*/);
    });
  });
});
