const { test, expect, nothingHappensFor } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { recordTransitions, transitions, forgetTransitions } = require('./screen-transitions.shared');

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
});
