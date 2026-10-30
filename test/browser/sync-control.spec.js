const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// An account is adopted from the recovery link `/#key=<token>`: the app asks
// `/api/identity/account` which account the token belongs to. A real account
// needs a burned invite grant, absent in a fresh environment, so that one
// answer is stubbed; the rest of the boot is the real one.
const answerAccount = (page) =>
  page.route('**/api/identity/account', (route) =>
    route.fulfill({ json: { id: 7 } }));

const signIn = async (page) => {
  await answerAccount(page);
  await page.goto('/#key=test-token');
};

const syncButton = (page) => page.getByRole('button', { name: 'Синхронизация' });
const pasteItem = (page) => page.getByRole('button', { name: 'Вставить ссылку восстановления' });
const connectItem = (page) => page.getByRole('button', { name: 'Подключить устройство' });

// Pastes `text` into the prompt the paste item asks with.
const pasteRecoveryLink = async (page, text) => {
  page.once('dialog', (dialog) => dialog.accept(text));
  await syncButton(page).click();
  await pasteItem(page).click();
};

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

  test('пользователь без аккаунта открывает меню синхронизации → в нём только вставка ссылки восстановления', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await page.goto('/home');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Когда он открывает меню «Синхронизация»', async () => {
      await syncButton(page).click();
    });

    await test.step('Тогда в меню есть «Вставить ссылку восстановления» и нет действий аккаунта', async () => {
      await expect(pasteItem(page)).toBeVisible();
      await expect(connectItem(page)).toHaveCount(0);
    });
  });

  test('пользователь без аккаунта вставляет ссылку восстановления → устройство входит в аккаунт', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await answerAccount(page);
      await page.goto('/home');
    });

    await test.step('Когда он вставляет ссылку восстановления', async () => {
      await pasteRecoveryLink(page, 'https://sprecha.de/#key=test-token');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Тогда меню предлагает «Подключить устройство»', async () => {
      await syncButton(page).click();
      await expect(connectItem(page)).toBeVisible();
      await expect(pasteItem(page)).toHaveCount(0);
    });
  });

  test('пользователь без аккаунта вставляет ссылку из QR подключения → подключение подтверждено', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await answerAccount(page);
      await page.goto('/home');
    });

    await test.step('Когда он вставляет ссылку с меткой подключения', async () => {
      await pasteRecoveryLink(page, 'https://sprecha.de/#key=test-token&pair=nonce-1');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
      await syncButton(page).click();
      await expect(connectItem(page)).toBeVisible();
    });

    await test.step('Тогда на устройстве записана квитанция подключения', async () => {
      const receipt = await page.evaluate(() =>
        db.use('user-db').get('pairing:nonce-1').then((doc) => doc.type, () => null));
      expect(receipt).toBe('pairing');
    });
  });

  test('пользователь без аккаунта вставляет не ссылку → сообщение, аккаунта нет', async ({ page }) => {
    let message;

    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await page.goto('/home');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeVisible();
    });

    await test.step('Когда он вставляет текст, который не ссылка восстановления', async () => {
      page.once('dialog', (dialog) => {
        page.once('dialog', (alert) => {
          message = alert.message();
          alert.accept();
        });
        dialog.accept('привет');
      });
      await syncButton(page).click();
      await pasteItem(page).click();
    });

    await test.step('Тогда он видит «Это не ссылка восстановления», и меню по-прежнему без аккаунта', async () => {
      await expect.poll(() => message).toBe('Это не ссылка восстановления');
      await syncButton(page).click();
      await expect(pasteItem(page)).toBeVisible();
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
