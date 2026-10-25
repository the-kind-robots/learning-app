const { test, expect } = require('./fixtures');

// The interface's text comes from the dictionary of one language, and the page
// says which: a screen reader picks its voice and a browser its hyphenation by
// `<html lang>` (#472).

test.describe('Язык интерфейса', () => {
  test('пользователь открывает приложение → страница объявлена русской, главная читается по-русски', async ({ page }) => {
    await test.step('Когда он открывает приложение', async () => {
      await page.goto('/');
    });

    await test.step('Тогда страница объявлена русской, а главная говорит по-русски', async () => {
      await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
      await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
      await expect(page.getByLabel('Слово (немецкий)')).toHaveAttribute('placeholder', 'Новое слово');
    });
  });
});
