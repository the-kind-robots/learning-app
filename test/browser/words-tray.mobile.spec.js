const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// Only the closed keyboard is measured here. Headless Chrome has no keyboard
// and so no `keyboard-inset-height` to give; the field on the keyboard and
// the button behind it are checked on a phone.
test.describe('Список слов на телефоне', () => {
  test('пользователь открывает список слов → клавиатура не перекрывает кнопку урока, она прижата к низу экрана', async ({ page }) => {
    const lessonButton = page.getByRole('button', { name: 'НАЧАТЬ УРОК' });

    await test.step('Дано слово «Haus» в словаре', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
    });

    await test.step('Когда он открывает список слов', async () => {
      await page.getByRole('button', { name: 'Список слов' }).click();
      await expect(page.getByRole('listitem').filter({ hasText: 'Haus' })).toBeVisible();
    });

    await test.step('Тогда страница просит клавиатуру наложиться поверх содержимого', async () => {
      const overlays = await page.evaluate(() =>
        'virtualKeyboard' in navigator ? navigator.virtualKeyboard.overlaysContent : null);
      expect(overlays).toBe(true);
    });

    await test.step('Тогда кнопка урока прижата к нижнему краю экрана', async () => {
      const footer = page.getByRole('contentinfo').filter({ has: lessonButton });
      const { position, bottom } = await footer.evaluate((el) => ({
        position: getComputedStyle(el).position,
        bottom: el.getBoundingClientRect().bottom,
      }));
      expect(position).toBe('fixed');
      expect(bottom).toBe(page.viewportSize().height);
    });
  });
});
