const { test, expect } = require('./fixtures');
const { seedWords } = require('./seed.shared');

// The lesson and the on-screen keyboard on a phone. An example is answered
// with tiles and raises no keyboard (GH-652).

// A document honours `autofocus` once, so the first lesson after a page load
// is the one that would raise the keyboard (#646).
test('ученик открыл первый урок после запуска на телефоне → поле ответа без фокуса', async ({ page }) => {
  await test.step('Дано только что запущенное приложение со словом', async () => {
    await page.goto('/home');
    await seedWords(page, ['der Hund']);
    await page.goto('/home');
  });

  await test.step('Когда он нажимает «НАЧАТЬ УРОК»', async () => {
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
  });

  await test.step('Тогда поле ответа видно и не в фокусе', async () => {
    const answer = page.getByRole('textbox', { name: 'Ответ на немецком' });
    await expect(answer).toBeVisible();
    await expect(answer).not.toBeFocused();
  });
});
