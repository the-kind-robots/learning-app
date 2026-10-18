const { test, expect, memoryReady } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// GH-598: the word dialog writes the translations one to a line.

const translations = (page) => page.getByLabel('Переводы, по одному на строку');

test.describe('Переводы слова', () => {
  test('пользователь пишет два перевода на разных строках и сохраняет → в списке оба', async ({ page }) => {
    await test.step('Дано слово «Abendessen» с переводом «ужин», открыт список слов', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Abendessen', 'ужин');
      await page.goto('/words');
      await expect(page.locator('.word-item')).toHaveCount(1, { timeout: 20000 });
    });

    await test.step('Когда он открывает слово, пишет «ужин» и «вечерняя еда» на двух строках и жмёт «Сохранить»', async () => {
      await page.locator('.word-item__display', { hasText: 'Abendessen' }).click();
      await translations(page).fill('ужин\n  вечерняя еда  \n\n');
      await page.getByRole('button', { name: 'Сохранить' }).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда в списке оба перевода', async () => {
      await expect(page.locator('.word-item')).toContainText('ужин, вечерняя еда');
    });

    await test.step('Когда он открывает слово снова', async () => {
      await page.locator('.word-item__display', { hasText: 'Abendessen' }).click();
    });

    await test.step('Тогда каждый перевод на своей строке, без лишних', async () => {
      await expect(translations(page)).toHaveValue('ужин\nвечерняя еда');
    });
  });

  test('пользователь нажимает Enter в переводах → появляется пустая строка, а при сохранении пустые строки отбрасываются', async ({ page }) => {
    await test.step('Дано слово «Abendessen» с переводом «ужин», открыто окно слова', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Abendessen', 'ужин');
      await page.goto('/words');
      await page.locator('.word-item__display', { hasText: 'Abendessen' }).click();
    });

    await test.step('Когда он ставит курсор в конец перевода и нажимает Enter', async () => {
      await translations(page).click();
      await page.keyboard.press('Control+End');
      await page.keyboard.press('Enter');
    });

    await test.step('Тогда в поле пустая строка, а «Сбросить» и «Сохранить» недоступны: пустая строка не изменение', async () => {
      await expect(translations(page)).toHaveValue('ужин\n');
      await expect(page.getByRole('button', { name: 'Сбросить' })).toBeDisabled();
      await expect(page.getByRole('button', { name: 'Сохранить' })).toBeDisabled();
    });

    await test.step('Когда он пишет вторую строку и сохраняет', async () => {
      await page.keyboard.type('ужинать');
      await expect(page.getByRole('button', { name: 'Сохранить' })).toBeEnabled();
      await page.getByRole('button', { name: 'Сохранить' }).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    await test.step('Тогда в списке оба перевода', async () => {
      await expect(page.locator('.word-item')).toContainText('ужин, ужинать');
    });
  });

  test('пользователь нажимает Enter в переводах → поле вырастает ровно на строку, а у одного перевода нет пустого места', async ({ page }) => {
    let one;

    await test.step('Дано слово «Abendessen» с одним переводом, открыто окно слова', async () => {
      await page.goto('/home');
      await memoryReady(page);
      await addWord(page, 'Abendessen', 'ужин');
      await page.goto('/words');
      await page.locator('.word-item__display', { hasText: 'Abendessen' }).click();
      one = (await translations(page).boundingBox()).height;
    });

    await test.step('Тогда поле в одну строку, не выше 40 px', async () => {
      expect(one).toBeLessThan(40);
    });

    await test.step('Когда он ставит курсор в конец и пишет вторую строку', async () => {
      await translations(page).click();
      await page.keyboard.press('Control+End');
      await page.keyboard.press('Enter');
      await page.keyboard.type('ужинать');
    });

    await test.step('Тогда поле выросло примерно на одну строку', async () => {
      const two = (await translations(page).boundingBox()).height;
      expect(two).toBeGreaterThan(one + 20);
      expect(two).toBeLessThan(one * 2 + 10);
    });
  });
});
