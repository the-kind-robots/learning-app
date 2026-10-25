const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { recordTransitions, transitions, forgetTransitions, animationsOf } = require('./screen-transitions.shared');

// Screens move only on a phone (#634); a window this wide changes them at once.

test.describe('Переходы между экранами на компьютере', () => {
  test('пользователь открывает список слов в широком окне → экран сменяется сразу', async ({ page }) => {
    await test.step('Дано главная со словом', async () => {
      await recordTransitions(page);
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await forgetTransitions(page);
    });

    await test.step('Когда он нажимает «Список слов»', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
    });

    await test.step('Тогда перехода нет', async () => {
      await expect(page.getByPlaceholder('Поиск')).toBeVisible();
      await nothingHappensFor(page, 500);
      expect(await transitions(page)).toEqual([]);
    });
  });

  test('пользователь неверно отвечает в широком окне → панель на месте сразу, ничего не дрожит', async ({ page }) => {
    await test.step('Дано урок', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      await page.goto('/lesson');
    });

    await test.step('Когда он проверяет неверный ответ', async () => {
      await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('falsch');
      await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
    });

    await test.step('Тогда панель на месте без движения', async () => {
      await expect(page.getByRole('heading', { name: 'Ваш ответ:' })).toBeVisible();
      expect(await animationsOf(page.locator('#lesson-footer > .lesson__footer'))).toEqual([]);
      expect(await animationsOf(page.getByText('falsch', { exact: true }))).toEqual([]);
    });
  });
});
