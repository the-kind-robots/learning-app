const { test, expect } = require('./fixtures');

// Chrome offers installing with `beforeinstallprompt` on its own schedule: right after the page's load,
// while the app may still be opening the learner's data (#814). The spec sends the offer itself, a plain
// cancelable event: the app reads nothing else of it until the button is tapped.

test.describe('Предложение установить приложение', () => {
  test('браузер предлагает установку, пока приложение запускается → на первом экране есть кнопка установки', async ({ page }) => {
    await test.step('Дано браузер предлагает установку, как только разобрал страницу', async () => {
      await page.addInitScript(() => {
        document.addEventListener('DOMContentLoaded', () => {
          window.dispatchEvent(new Event('beforeinstallprompt', { cancelable: true }));
        });
      });
    });

    await test.step('Когда пользователь открывает главную', async () => {
      await page.goto('/home');
    });

    await test.step('Тогда в шапке есть кнопка установки', async () => {
      await expect(page.getByRole('button', { name: 'Установить приложение' })).toBeVisible();
    });
  });

  test('браузер предлагает установку на открытой главной → появляется кнопка установки', async ({ page }) => {
    await test.step('Дано открыта главная', async () => {
      await page.goto('/home');
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    });

    await test.step('Когда браузер предлагает установку', async () => {
      await page.evaluate(() => {
        window.dispatchEvent(new Event('beforeinstallprompt', { cancelable: true }));
      });
    });

    await test.step('Тогда в шапке есть кнопка установки', async () => {
      await expect(page.getByRole('button', { name: 'Установить приложение' })).toBeVisible();
    });
  });
});
