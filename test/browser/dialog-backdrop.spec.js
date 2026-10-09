const { test, expect } = require('./fixtures');

// The sync-menu dialog only exists once an account is provisioned, which
// needs a burned invite grant — absent in a fresh environment. The
// install guide is the dialog reachable without an account; an iOS user
// agent makes the install button render and routes its click to the guide
// instead of the native prompt.
test.use({
  userAgent:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
    'AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
});

test.describe('Окно установки приложения', () => {
  test('пользователь нажимает на фон вокруг окна → окно закрывается, а нажатие внутри — нет', async ({ page }) => {
    const title = page.getByRole('heading', { name: 'Install Sprecha' });

    await test.step('Дано главная без аккаунта, открыта инструкция по установке', async () => {
      await page.goto('/home');
      // This dialog and not the sync menu: no account, no sync button.
      await expect(page.getByRole('button', { name: 'Синхронизация' })).toHaveCount(0);
      await page.getByRole('button', { name: 'Установить приложение' }).click();
      await expect(title).toBeVisible();
    });

    await test.step('Когда он нажимает внутри окна', async () => {
      await title.click();
    });

    await test.step('Тогда окно остаётся', async () => {
      await expect(title).toBeVisible();
    });

    await test.step('Когда он нажимает на фон в углу', async () => {
      await page.locator('#app-install-guide').click({ position: { x: 8, y: 8 } });
    });

    await test.step('Тогда окно закрывается', async () => {
      await expect(title).toBeHidden();
    });
  });
});
