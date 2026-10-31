const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { stubAccountCopy } = require('./account.shared');

// The lesson's light bulb green, and the grey of a control that is off.
const GREEN = 'rgb(88, 204, 2)';
const GREY = 'rgb(175, 175, 175)';

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

  test('пользователь без аккаунта открывает главную → кнопка видна и серая', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта', async () => {
      await page.goto('/home');
    });

    await test.step('Тогда на главной кнопка «Синхронизация» видна и серая, без свечения', async () => {
      await expect(syncButton(page)).toBeVisible();
      await expect(syncButton(page)).toHaveCSS('color', GREY);
      await expect(syncButton(page)).toHaveCSS('filter', 'none');
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

test.describe('Иконка синхронизации', () => {
  test('пользователь с аккаунтом в сети → иконка рядом с лого, зелёная со свечением', async ({ page }) => {
    await test.step('Дано пользователь с аккаунтом в сети', async () => {
      await stubAccountCopy(page.context());
      await signIn(page);
    });

    await test.step('Тогда иконка стоит сразу за лого, зелёная со свечением', async () => {
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await expect(syncButton(page)).not.toHaveCSS('filter', 'none');
      await expect(syncButton(page)).toHaveCSS('opacity', '1');
      const logo = await page.getByRole('button', { name: /^Sprecha/ }).boundingBox();
      const icon = await syncButton(page).boundingBox();
      expect(icon.x - (logo.x + logo.width)).toBeLessThan(16);
      expect(Math.abs(icon.y + icon.height / 2 - (logo.y + logo.height / 2))).toBeLessThan(2);
    });
  });

  test('пользователь с аккаунтом теряет сеть → иконка бледно-зелёная, сеть вернулась → снова со свечением', async ({ page, context }) => {
    await test.step('Дано пользователь с аккаунтом в сети', async () => {
      await stubAccountCopy(context);
      await signIn(page);
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
    });

    await test.step('Когда сеть пропадает', async () => {
      await context.setOffline(true);
    });

    await test.step('Тогда иконка зелёная, бледная и без свечения', async () => {
      await expect(syncButton(page)).toHaveCSS('color', GREEN);
      await expect(syncButton(page)).toHaveCSS('opacity', '0.4');
      await expect(syncButton(page)).toHaveCSS('filter', 'none');
    });

    await test.step('Когда сеть возвращается', async () => {
      await context.setOffline(false);
    });

    await test.step('Тогда иконка снова зелёная со свечением', async () => {
      await expect(syncButton(page)).toHaveCSS('opacity', '1');
      await expect(syncButton(page)).not.toHaveCSS('filter', 'none');
    });
  });
});

test.describe('Сохранить мои слова', () => {
  const inviteForm = (page) => page.getByRole('dialog', { name: 'Чтобы слова не потерялись' });

  test('пользователь без аккаунта открывает меню синхронизации → в нём «Сохранить мои слова»', async ({ page }) => {
    await test.step('Дано пользователь без аккаунта на главной', async () => {
      await page.goto('/home');
    });

    await test.step('Когда он открывает меню синхронизации', async () => {
      await syncButton(page).click();
    });

    await test.step('Тогда в меню есть «Сохранить мои слова»', async () => {
      await expect(page.getByRole('button', { name: 'Сохранить мои слова' })).toBeVisible();
    });
  });

  test('пользователь без аккаунта пишет пару тёплых слов и отправляет → форма закрывается, запрос в консоли', async ({ page }) => {
    const logged = [];
    page.on('console', (message) => {
      if (message.type() === 'info') logged.push(message.text());
    });

    await test.step('Дано открыта форма «Чтобы слова не потерялись»', async () => {
      await page.goto('/home');
      await syncButton(page).click();
      await page.getByRole('button', { name: 'Сохранить мои слова' }).click();
      await expect(inviteForm(page)).toBeVisible();
    });

    await test.step('Тогда поле пустое', async () => {
      await expect(inviteForm(page).getByRole('textbox')).toHaveValue('');
    });

    await test.step('Когда он пишет пару слов и нажимает «ОТПРАВИТЬ»', async () => {
      await inviteForm(page).getByRole('textbox').fill('Спасибо за приложение!');
      await inviteForm(page).getByRole('button', { name: 'ОТПРАВИТЬ' }).click();
    });

    await test.step('Тогда форма закрывается, а запрос записан в консоль', async () => {
      await expect(inviteForm(page)).toHaveCount(0);
      expect(logged.some((text) => text.includes('Спасибо за приложение!'))).toBe(true);
    });
  });

  test('пользователь отправляет пустую форму «Чтобы слова не потерялись» → форма остаётся', async ({ page }) => {
    await test.step('Дано открыта форма «Чтобы слова не потерялись»', async () => {
      await page.goto('/home');
      await syncButton(page).click();
      await page.getByRole('button', { name: 'Сохранить мои слова' }).click();
    });

    await test.step('Когда он нажимает «ОТПРАВИТЬ», ничего не написав', async () => {
      await inviteForm(page).getByRole('button', { name: 'ОТПРАВИТЬ' }).click();
    });

    await test.step('Тогда форма остаётся открытой', async () => {
      await expect(inviteForm(page)).toBeVisible();
    });
  });
});
