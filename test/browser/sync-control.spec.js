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

    await test.step('Когда он открывает список слов', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page.getByRole('button', { name: 'Закрыть' })).toBeVisible();
    });

    await test.step('Тогда кнопки «Синхронизация» нет', async () => {
      await expect(syncButton(page)).toHaveCount(0);
    });

    await test.step('Когда он возвращается и открывает наборы', async () => {
      await page.goto('/home');
      await expect(syncButton(page)).toBeVisible();
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(page.getByRole('button', { name: 'Закрыть' })).toBeVisible();
    });

    await test.step('Тогда кнопки «Синхронизация» нет', async () => {
      await expect(syncButton(page)).toHaveCount(0);
    });

    await test.step('Когда он возвращается и начинает урок', async () => {
      await page.goto('/home');
      await expect(syncButton(page)).toBeVisible();
      await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
      await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
    });

    await test.step('Тогда кнопки «Синхронизация» нет', async () => {
      await expect(syncButton(page)).toHaveCount(0);
    });
  });
});
