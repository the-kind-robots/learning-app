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

// The page sets `interactive-widget=resizes-content`: an open keyboard is a
// shorter viewport, so a shorter viewport stands in for it (#763).
test('ученик на телефоне нажал на поле ответа и открылась клавиатура → слово задания того же размера, подсказка над ним на месте', async ({ page }) => {
  let before;

  await test.step('Дано начатый урок со словом', async () => {
    await page.goto('/home');
    await seedWords(page, ['der Hund']);
    await page.goto('/home');
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    before = await page.getByText('перевод', { exact: true }).boundingBox();
  });

  await test.step('Когда он нажимает на поле и клавиатура укорачивает экран', async () => {
    await page.getByRole('textbox', { name: 'Ответ на немецком' }).tap();
    await page.setViewportSize({ width: 390, height: 430 });
  });

  await test.step('Тогда слово задания той же высоты, подсказка видна над ним', async () => {
    await expect(page.getByRole('textbox', { name: 'Ответ на немецком' })).toBeFocused();
    const after = await page.getByText('перевод', { exact: true }).boundingBox();
    expect(after.height).toBeCloseTo(before.height, 0);
    await expect(page.getByText('Переведите слово на немецкий')).toBeInViewport();
  });
});
