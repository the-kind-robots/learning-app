const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// An account is adopted from the recovery link `/#key=<token>`: the app asks
// `/api/identity/account` which account the token belongs to. A real account
// needs a burned invite grant, absent in a fresh environment, so that one
// answer is stubbed; the rest of the boot is the real one.
const signIn = async (page) => {
  await page.route('**/api/identity/account', (route) =>
    route.fulfill({ json: { id: 7 } }));
  await page.goto('/#key=test-token');
};

const syncButton = (page) => page.getByRole('button', { name: 'Синхронизация' });

// Screens that take the whole page: the button that opens each, and what shows it open.
const closeButton = (page) => page.getByRole('button', { name: 'Закрыть' });
const SCREENS = [
  ['список слов', 'Список слов', closeButton],
  ['наборы', 'Открыть наборы', closeButton],
  ['урок', 'НАЧАТЬ УРОК', (page) => page.getByRole('progressbar', { name: 'Прогресс урока' })],
];

test.describe('Кнопка «Синхронизация»', () => {
  test('пользователь с аккаунтом открывает главную → кнопка видна', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом', async () => {
      await signIn(page);
    });

    await test.step('Когда он на главной', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Тогда кнопка «Синхронизация» видна', async () => {
      await expect(syncButton(page)).toBeVisible();
    });
  });

  test('пользователь без аккаунта открывает главную → кнопки нет', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта', async () => {
      await page.goto('/home');
    });

    await test.step('Тогда на главной нет кнопки «Синхронизация»', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      await expect(syncButton(page)).toHaveCount(0);
    });
  });

  test('пользователь с аккаунтом открывает слова, наборы или урок → кнопки нет', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом и словом «Haus» на главной', async () => {
      await signIn(page);
      await expect(syncButton(page)).toBeVisible();
      await addWord(page, 'Haus', 'дом');
    });

    for (const [screen, opener, shown] of SCREENS) {
      await test.step(`Когда он открывает ${screen}`, async () => {
        await page.getByRole('button', { name: opener }).click();
        await expect(shown(page)).toBeVisible();
      });

      await test.step('Тогда кнопки «Синхронизация» нет', async () => {
        await expect(syncButton(page)).toHaveCount(0);
      });

      await test.step('Когда он закрывает экран', async () => {
        await page.getByRole('button', { name: 'Закрыть' }).click();
        await expect(syncButton(page)).toBeVisible();
      });
    }
  });
});
