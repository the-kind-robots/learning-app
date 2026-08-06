const { test, expect } = require('@playwright/test');

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

test('install guide dialog closes on a backdrop click', async ({ page }) => {
  await page.goto('/home');

  // Document why this dialog and not the sync menu: no account, no button.
  await expect(page.getByRole('button', { name: 'Синхронизация' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Установить приложение' }).click();
  const title = page.getByRole('heading', { name: 'Install Sprecha' });
  await expect(title).toBeVisible();

  // A click inside the card must not dismiss.
  await title.click();
  await expect(title).toBeVisible();

  // A click on the backdrop (top-left corner, outside the centered card) must.
  await page.locator('#app-install-guide').click({ position: { x: 8, y: 8 } });
  await expect(title).toBeHidden();
});
